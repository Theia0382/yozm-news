package net.yozmnews.report.domain.prompt;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;

import net.yozmnews.llm.TokenEstimate;
import net.yozmnews.platform.CodePoints;
import net.yozmnews.report.domain.alias.ReportAliases;
import net.yozmnews.report.domain.input.InputSection;
import net.yozmnews.report.domain.input.InterestsSection;
import net.yozmnews.report.domain.input.PriorReport;
import net.yozmnews.report.domain.input.PriorSection;
import net.yozmnews.report.domain.input.ReportInput;
import net.yozmnews.report.domain.input.StorylinesSection;
import net.yozmnews.report.domain.input.UserStorylineInterestLine;
import net.yozmnews.report.domain.input.UserStorylineMaterial;
import net.yozmnews.report.domain.input.UserStorylineSummary;
import net.yozmnews.report.domain.prompt.FittedInput.Trimmed;
import net.yozmnews.storyline.StorylineId;

/**
 * 입력 맞추기 — 덜어 내기 순서의 정본 · docs/specs/report.md §5 · report-R22
 * <p>
 * 크기는 그 입력으로 그린 사용자 구획({@link ReportPrompts#userContent})의 코드 포인트를 {@link TokenEstimate} 로 어림한 토큰이다. 예산을 넘으면
 * 직전 글 → 추적하지 않는 사용자 스토리라인의 정리 → 사건 줄 → 흐름 요약 → 추적한 사용자 스토리라인의 순서로 덜어 내고, 단계마다 예산 안에 들면
 * 멈춘다. 갈래는 없어지지 않는다 — 갈래마다 줄 하나는 남긴다. 직전 글은 편마다 몫 안에 드는 가장 긴 본문 길이로, 사건 줄 · 추적한 사용자
 * 스토리라인은 예산 안에 드는 가장 적은 양만 덜어 낸다(크기는 덜어 낼수록 줄어 이분 탐색으로 찾는다). 추적하지 않는 사용자 스토리라인의 정리와 사용자 스토리라인
 * 요약은 그 단계에서 모두 뺀다. 사용자 · 기간 · 시간대 · 스냅숏은 바꾸지 않는다.
 */
public final class ReportInputFit {

	private ReportInputFit() {
	}

	/**
	 * 입력을 사용자 구획 예산 안으로 맞춘다
	 * @param budget 사용자 구획 예산({@link ReportInputBudget#userContent})
	 * @throws IllegalArgumentException 예산이 음수일 때
	 */
	public static FittedInput fit(ReportInput input, int budget) {
		Objects.requireNonNull(input, "input");
		if (budget < 0) {
			throw new IllegalArgumentException("예산이 음수다: " + budget);
		}
		if (fits(input, budget)) {
			return new FittedInput(input, Trimmed.NONE, false);
		}
		Work work = new Work(input);
		int priorsCut = work.cutPriors(ReportInputBudget.priorShare(budget));
		int summariesDropped = 0;
		int eventsDropped = 0;
		int overviewsDropped = 0;
		int userStorylinesDropped = 0;
		boolean done = fits(work.build(), budget);
		if (!done) {
			summariesDropped += work.dropUntrackedSummaries();
			done = fits(work.build(), budget);
		}
		if (!done) {
			eventsDropped = work.shortenEventLines(budget);
			done = fits(work.build(), budget);
		}
		if (!done) {
			overviewsDropped = work.dropUserStorylineOverviews();
			done = fits(work.build(), budget);
		}
		if (!done) {
			int[] dropped = work.dropUserStorylines(budget);
			userStorylinesDropped = dropped[0];
			summariesDropped += dropped[1];
			done = fits(work.build(), budget);
		}
		return new FittedInput(work.build(), new Trimmed(priorsCut, summariesDropped, eventsDropped, overviewsDropped,
				userStorylinesDropped), !done);
	}

	/** 사용자 구획의 토큰 어림 */
	static int tokens(ReportInput input) {
		String content = ReportPrompts.userContent(input, ReportAliases.of(input));
		return TokenEstimate.tokens(content.codePointCount(0, content.length()));
	}

	private static boolean fits(ReportInput input, int budget) {
		return tokens(input) <= budget;
	}

	/** {@code [lo, hi]} 에서 조건을 처음 만족하는 가장 작은 값 — 조건은 값이 클수록 참이 되기 쉽다. 하나도 없으면 {@code hi} */
	private static int smallest(int lo, int hi, IntPredicate ok) {
		int low = lo;
		int high = hi;
		while (low < high) {
			int mid = (low + high) >>> 1;
			if (ok.test(mid)) {
				high = mid;
			}
			else {
				low = mid + 1;
			}
		}
		return low;
	}

	/** 맞추는 중인 입력 — 갈래 순서는 원래 입력의 것 */
	private static final class Work {

		private final ReportInput original;

		private final ZoneId zone;

		private List<PriorReport> priors;

		private List<UserStorylineMaterial> userStorylines;

		private List<UserStorylineInterestLine> interestUserStorylines;

		Work(ReportInput original) {
			this.original = original;
			this.zone = original.zone();
			this.priors = new ArrayList<>(original.priors());
			this.userStorylines = List.of();
			this.interestUserStorylines = List.of();
			for (InputSection section : original.sections()) {
				switch (section) {
					case StorylinesSection storylines -> this.userStorylines = new ArrayList<>(storylines.userStorylines());
					case InterestsSection interests -> this.interestUserStorylines = new ArrayList<>(interests.userStorylines());
				}
			}
		}

		ReportInput build() {
			return build(this.priors, this.userStorylines, this.interestUserStorylines);
		}

		private ReportInput build(List<PriorReport> priors, List<UserStorylineMaterial> userStorylines,
				List<UserStorylineInterestLine> interestUserStorylines) {
			List<InputSection> sections = new ArrayList<>();
			for (InputSection section : this.original.sections()) {
				sections.add(switch (section) {
					case StorylinesSection storylines -> new StorylinesSection(userStorylines);
					case InterestsSection interests ->
						new InterestsSection(interests.topics(), interestUserStorylines, interests.subscriptionChanges());
				});
			}
			return new ReportInput(this.original.user(), this.original.period(), this.zone, sections, priors,
					this.original.statsSnapshot(), this.original.previous());
		}

		/**
		 * 1 단계 — 싣는 직전 글마다 몫({@code share} 를 싣는 편 수로 나눈 것)을 넘는 편의 갈래 본문을 같은 길이 상한까지 자른다
		 * @return 자른 편 수
		 */
		int cutPriors(int share) {
			long shown = this.priors.stream().filter(ReportPrompts::shown).count();
			if (shown == 0) {
				return 0;
			}
			int perPrior = (int) (share / shown);
			int cut = 0;
			List<PriorReport> next = new ArrayList<>();
			for (PriorReport prior : this.priors) {
				if (!ReportPrompts.shown(prior) || priorTokens(prior) <= perPrior) {
					next.add(prior);
					continue;
				}
				int longest = prior.sections().stream().mapToInt(s -> codePoints(s.body())).max().orElse(0);
				if (longest == 0) {
					// 자를 본문이 없다 — 섹션 제목은 자르지 않는다
					next.add(prior);
					continue;
				}
				int keep = longest - smallest(0, longest, drop -> priorTokens(cutBodies(prior, longest - drop)) <= perPrior);
				next.add(cutBodies(prior, keep));
				cut++;
			}
			this.priors = next;
			return cut;
		}

		/**
		 * 2 단계 — 추적한 사용자 스토리라인에 없는 헤드의 사용자 스토리라인 관심 줄에서 정리를 뺀다
		 * @return 뺀 정리 수
		 */
		int dropUntrackedSummaries() {
			Set<StorylineId> tracked = trackedHeads(this.userStorylines);
			int dropped = 0;
			List<UserStorylineInterestLine> next = new ArrayList<>();
			for (UserStorylineInterestLine line : this.interestUserStorylines) {
				if (line.summary() != null && !tracked.contains(line.head())) {
					next.add(withoutSummary(line));
					dropped++;
				}
				else {
					next.add(line);
				}
			}
			this.interestUserStorylines = next;
			return dropped;
		}

		/**
		 * 3 단계 — 모든 사용자 스토리라인의 사건 줄을 같은 수까지 줄인다(사용자 스토리라인마다 앞을 남긴다)
		 * @return 뺀 사건 줄 수
		 */
		int shortenEventLines(int budget) {
			int most = this.userStorylines.stream().mapToInt(userStoryline -> userStoryline.events().size()).max().orElse(0);
			if (most == 0) {
				return 0;
			}
			// 남길 수 k 가 클수록 크다 — 남길 수를 줄이는 양(most − k)의 가장 작은 값을 찾는다
			int cut = smallest(1, most, drop -> fits(build(this.priors, shortened(most - drop), this.interestUserStorylines), budget));
			List<UserStorylineMaterial> next = shortened(most - cut);
			int dropped = 0;
			for (int i = 0; i < this.userStorylines.size(); i++) {
				dropped += this.userStorylines.get(i).events().size() - next.get(i).events().size();
			}
			this.userStorylines = next;
			return dropped;
		}

		/**
		 * 4 단계 — 추적한 사용자 스토리라인의 정리에서 흐름 요약을 비운다(제목 · 최근 전개는 남긴다)
		 * @return 비운 사용자 스토리라인 수
		 */
		int dropUserStorylineOverviews() {
			int dropped = 0;
			List<UserStorylineMaterial> next = new ArrayList<>();
			for (UserStorylineMaterial userStoryline : this.userStorylines) {
				UserStorylineSummary summary = userStoryline.summary();
				if (summary == null || summary.summary().isEmpty()) {
					next.add(userStoryline);
					continue;
				}
				next.add(new UserStorylineMaterial(userStoryline.head(), userStoryline.tops(),
						new UserStorylineSummary(summary.title(), "", summary.recent(), summary.updatedAt()), userStoryline.headState(),
						userStoryline.trackedInPeriod(), userStoryline.articles(), userStoryline.events()));
				dropped++;
			}
			this.userStorylines = next;
			return dropped;
		}

		/**
		 * 5 단계 — 기간 보도량이 적은 사용자 스토리라인부터(같으면 헤드 ID 가 큰 것부터) 뺀다. 하나는 남긴다. 뺀 사용자 스토리라인의 헤드가 사용자 스토리라인 관심 줄에 있으면 그 줄의 정리도 뺀다
		 * @return {뺀 사용자 스토리라인 수, 뺀 정리 수}
		 */
		int[] dropUserStorylines(int budget) {
			if (this.userStorylines.size() <= 1) {
				return new int[] { 0, 0 };
			}
			List<UserStorylineMaterial> order = this.userStorylines.stream()
				.sorted(Comparator.comparingLong((UserStorylineMaterial userStoryline) -> userStoryline.articles().current())
					.thenComparing(Comparator.comparingLong((UserStorylineMaterial userStoryline) -> userStoryline.head().value()).reversed()))
				.toList();
			int max = this.userStorylines.size() - 1;
			int count = smallest(1, max, k -> fits(droppingUserStorylines(order.subList(0, k)), budget));
			Set<StorylineId> gone = trackedHeads(order.subList(0, count));
			int summaries = (int) this.interestUserStorylines.stream()
				.filter(line -> line.summary() != null && gone.contains(line.head()))
				.count();
			ReportInput dropped = droppingUserStorylines(order.subList(0, count));
			this.userStorylines = userStorylinesOf(dropped);
			this.interestUserStorylines = interestUserStorylinesOf(dropped);
			return new int[] { count, summaries };
		}

		private ReportInput droppingUserStorylines(List<UserStorylineMaterial> gone) {
			Set<StorylineId> heads = trackedHeads(gone);
			List<UserStorylineMaterial> kept = this.userStorylines.stream().filter(userStoryline -> !heads.contains(userStoryline.head())).toList();
			List<UserStorylineInterestLine> lines = this.interestUserStorylines.stream()
				.map(line -> heads.contains(line.head()) && line.summary() != null ? withoutSummary(line) : line)
				.toList();
			return build(this.priors, kept, lines);
		}

		private List<UserStorylineMaterial> shortened(int keep) {
			return this.userStorylines.stream()
				.map(userStoryline -> userStoryline.events().size() <= keep ? userStoryline
						: new UserStorylineMaterial(userStoryline.head(), userStoryline.tops(), userStoryline.summary(), userStoryline.headState(),
								userStoryline.trackedInPeriod(), userStoryline.articles(), userStoryline.events().subList(0, keep)))
				.toList();
		}

		private int priorTokens(PriorReport prior) {
			return TokenEstimate.tokens(codePoints(ReportPrompts.priorText(prior, this.zone)));
		}

		private static PriorReport cutBodies(PriorReport prior, int keep) {
			List<PriorSection> sections = prior.sections()
				.stream()
				.map(section -> new PriorSection(section.kind(), section.title(), CodePoints.prefix(section.body(), keep)))
				.toList();
			return new PriorReport(prior.id(), prior.kind(), prior.start(), prior.end(), sections);
		}

		private static UserStorylineInterestLine withoutSummary(UserStorylineInterestLine line) {
			return new UserStorylineInterestLine(line.head(), null, line.start(), line.end(), line.shift());
		}

		private static Set<StorylineId> trackedHeads(List<UserStorylineMaterial> userStorylines) {
			Set<StorylineId> heads = new HashSet<>();
			userStorylines.forEach(userStoryline -> heads.add(userStoryline.head()));
			return heads;
		}

		private static List<UserStorylineMaterial> userStorylinesOf(ReportInput input) {
			for (InputSection section : input.sections()) {
				if (section instanceof StorylinesSection storylines) {
					return new ArrayList<>(storylines.userStorylines());
				}
			}
			return List.of();
		}

		private static List<UserStorylineInterestLine> interestUserStorylinesOf(ReportInput input) {
			for (InputSection section : input.sections()) {
				if (section instanceof InterestsSection interests) {
					return new ArrayList<>(interests.userStorylines());
				}
			}
			return List.of();
		}

		private static int codePoints(String text) {
			return text.codePointCount(0, text.length());
		}

	}

}
