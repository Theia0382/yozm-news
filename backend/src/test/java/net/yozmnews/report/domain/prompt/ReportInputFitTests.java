package net.yozmnews.report.domain.prompt;

import static net.yozmnews.report.ReportFixtures.coverage;
import static net.yozmnews.report.ReportFixtures.userStoryline;
import static net.yozmnews.report.ReportFixtures.userStorylines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import net.yozmnews.event.EventId;
import net.yozmnews.report.InterestShift;
import net.yozmnews.report.ReportFixtures;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.input.Count;
import net.yozmnews.report.domain.input.EventLine;
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
import net.yozmnews.storyline.StorylineState;
import org.junit.jupiter.api.Test;

/**
 * 입력 맞추기 — docs/specs/report.md §5 · report-R22
 * <p>
 * 입력마다 한 단계가 크기를 차지하게 만들어 그 단계에서 멈추는지와, 그 단계가 무엇을 남겼는지를 단언한다 — 크기를 다시 계산하지 않는다. 예산은
 * 차지하는 덩어리와 나머지의 어림(코드 포인트 하나에 토큰 하나)보다 넉넉히 떨어진 리터럴이다.
 */
class ReportInputFitTests {

	private static final Instant UPDATED = Instant.parse("2026-11-20T00:00:00Z");

	@Test
	void leavesAnInputWithinTheBudgetAsItIs() {
		ReportInput input = ReportFixtures.input(userStorylines(userStoryline(31, 902)), coverage());

		FittedInput fitted = ReportInputFit.fit(input, 72_000);

		assertThat(fitted.input()).isSameAs(input);
		assertThat(fitted.trimmed()).isEqualTo(Trimmed.NONE);
		assertThat(fitted.trimmedAnything()).isFalse();
		assertThat(fitted.overBudget()).isFalse();
	}

	@Test
	void cutsTheBodiesOfEachPriorToItsShareAndKeepsOverviewsAndTitles() {
		// 직전 글 두 편 — 본문 5,000 자씩. 예산 4,000 의 몫 1,200 을 두 편이 600 씩
		PriorReport first = prior(1, "가".repeat(5_000));
		PriorReport second = prior(2, "나".repeat(5_000));
		ReportInput input = input(List.of(coverage()), List.of(first, second));

		FittedInput fitted = ReportInputFit.fit(input, 4_000);

		assertThat(fitted.trimmed()).isEqualTo(new Trimmed(2, 0, 0, 0, 0));
		assertThat(fitted.overBudget()).isFalse();
		for (PriorReport prior : fitted.input().priors()) {
			assertThat(prior.overview()).isEqualTo("지난 개요");
			assertThat(prior.sections()).singleElement().satisfies(section -> {
				assertThat(section.title()).isEqualTo("지난 제목");
				assertThat(section.body().length()).isBetween(400, 600);
			});
		}
	}

	@Test
	void doesNotPassTheShareLeftByAShortPriorToALongOne() {
		// 짧은 편이 몫 600 을 다 쓰지 않아도 긴 편은 자기 몫 600 까지만 — 남은 몫은 재료가 쓴다
		PriorReport shortPrior = prior(1, "가".repeat(50));
		PriorReport longPrior = prior(2, "나".repeat(5_000));
		ReportInput input = input(List.of(coverage()), List.of(shortPrior, longPrior));

		FittedInput fitted = ReportInputFit.fit(input, 4_000);

		assertThat(fitted.trimmed()).isEqualTo(new Trimmed(1, 0, 0, 0, 0));
		assertThat(fitted.input().priors().get(0)).isEqualTo(shortPrior);
		assertThat(fitted.input().priors().get(1).sections()).singleElement()
			.satisfies(section -> assertThat(section.body().length()).isBetween(400, 600));
	}

	@Test
	void dropsTheSummariesOfUntrackedUserStorylinesFirstAmongTheMaterial() {
		// 추적하지 않는 사용자 스토리라인 셋의 정리가 3,000 자씩 — 추적한 사용자 스토리라인(헤드 1)의 관심 줄 정리는 사용자 구획에 실리지 않으므로 그대로 둔다
		List<UserStorylineInterestLine> lines = new ArrayList<>();
		lines.add(new UserStorylineInterestLine(new StorylineId(1), summary("다".repeat(3_000)), 0.2, 0.4, InterestShift.NONE));
		LongStream.of(11, 12, 13)
			.forEach(head -> lines.add(new UserStorylineInterestLine(new StorylineId(head), summary("다".repeat(3_000)), null, 0.3,
					InterestShift.ENTERED)));
		ReportInput input = input(List.of(userStorylines(userStoryline(1)), new InterestsSection(List.of(), lines, List.of())), List.of());

		FittedInput fitted = ReportInputFit.fit(input, 3_000);

		assertThat(fitted.trimmed()).isEqualTo(new Trimmed(0, 3, 0, 0, 0));
		assertThat(fitted.overBudget()).isFalse();
		List<UserStorylineInterestLine> after = interests(fitted.input()).userStorylines();
		assertThat(after).hasSize(4);
		assertThat(after.get(0).summary()).isNotNull();
		assertThat(after.subList(1, 4)).allSatisfy(line -> assertThat(line.summary()).isNull());
	}

	@Test
	void shortensEveryUserStorylineToTheSameNumberOfEventLinesKeepingTheFront() {
		// 사용자 스토리라인 넷 · 사건 줄 열씩 — 제목 300 자
		List<UserStorylineMaterial> userStorylines = LongStream.rangeClosed(1, 4).mapToObj(head -> userStorylineWithEvents(head, 10, 300)).toList();
		ReportInput input = input(List.of(new StorylinesSection(userStorylines)), List.of());

		FittedInput fitted = ReportInputFit.fit(input, 6_000);

		List<UserStorylineMaterial> after = storylines(fitted.input()).userStorylines();
		int kept = after.get(0).events().size();
		assertThat(kept).isBetween(1, 9);
		assertThat(after).allSatisfy(userStoryline -> assertThat(userStoryline.events()).hasSize(kept));
		assertThat(after.get(2).events()).isEqualTo(userStorylines.get(2).events().subList(0, kept));
		assertThat(fitted.trimmed()).isEqualTo(new Trimmed(0, 0, 40 - 4 * kept, 0, 0));
		assertThat(fitted.overBudget()).isFalse();
	}

	@Test
	void emptiesTheUserStorylineOverviewsKeepingTitlesAndRecentDevelopments() {
		// 사용자 스토리라인 열 · 사건 줄 없음 · 흐름 요약 1,000 자씩
		List<UserStorylineMaterial> userStorylines = LongStream.rangeClosed(1, 10)
			.mapToObj(head -> trackedUserStoryline(head, 10, new UserStorylineSummary("제목 " + head, "라".repeat(1_000), "최근 " + head,
					UPDATED)))
			.toList();
		ReportInput input = input(List.of(new StorylinesSection(userStorylines)), List.of());

		FittedInput fitted = ReportInputFit.fit(input, 2_000);

		assertThat(fitted.trimmed()).isEqualTo(new Trimmed(0, 0, 0, 10, 0));
		assertThat(fitted.overBudget()).isFalse();
		assertThat(storylines(fitted.input()).userStorylines()).allSatisfy(userStoryline -> {
			assertThat(userStoryline.summary().summary()).isEmpty();
			assertThat(userStoryline.summary().title()).startsWith("제목 ");
			assertThat(userStoryline.summary().recent()).startsWith("최근 ");
		});
	}

	@Test
	void dropsTrackedUserStorylinesWithTheLeastCoverageAndTheirInterestSummaries() {
		// 사용자 스토리라인 다섯 — 최근 전개 1,000 자씩(덜어 낼 수 없는 칸), 보도량 50 · 10 · 30 · 10 · 40(헤드 1 ~ 5)
		long[] articles = { 50, 10, 30, 10, 40 };
		List<UserStorylineMaterial> userStorylines = IntStream.range(0, 5)
			.mapToObj(i -> trackedUserStoryline(i + 1, articles[i], new UserStorylineSummary("t", "", "마".repeat(1_000), UPDATED)))
			.toList();
		// 헤드 4 의 사용자 스토리라인 관심 줄 — 추적한 사용자 스토리라인이라 정리는 실리지 않다가, 사용자 스토리라인을 빼면 같이 뺀다
		InterestsSection interests = new InterestsSection(List.of(),
				List.of(new UserStorylineInterestLine(new StorylineId(4), summary("짧은 요약"), 0.2, 0.5, InterestShift.ENTERED)),
				List.of());
		ReportInput input = input(List.of(new StorylinesSection(userStorylines), interests), List.of());

		FittedInput fitted = ReportInputFit.fit(input, 2_500);

		// 보도량이 적은 순, 같으면 헤드가 큰 것부터 — 4(10) · 2(10) · 3(30)
		assertThat(storylines(fitted.input()).userStorylines()).extracting(userStoryline -> userStoryline.head().value()).containsExactly(1L, 5L);
		assertThat(fitted.trimmed()).isEqualTo(new Trimmed(0, 1, 0, 0, 3));
		assertThat(interests(fitted.input()).userStorylines()).singleElement()
			.satisfies(line -> assertThat(line.summary()).isNull());
		assertThat(fitted.overBudget()).isFalse();
	}

	@Test
	void keepsALineInEverySectionAndReportsOverBudgetWhenNothingFits() {
		List<UserStorylineInterestLine> lines = LongStream.range(100, 103)
			.mapToObj(head -> new UserStorylineInterestLine(new StorylineId(head), null, 0.1, 0.6, InterestShift.ENTERED))
			.toList();
		ReportInput input = input(List.of(userStorylines(userStoryline(31, 902), userStoryline(32, 903)), coverage(),
				new InterestsSection(List.of(), lines, List.of())), List.of());

		FittedInput fitted = ReportInputFit.fit(input, 0);

		assertThat(fitted.overBudget()).isTrue();
		ReportInput after = fitted.input();
		assertThat(after.sections()).extracting(InputSection::kind)
			.containsExactly(ReportSectionKind.STORYLINES, ReportSectionKind.COVERAGE, ReportSectionKind.INTERESTS);
		assertThat(storylines(after).userStorylines()).hasSize(1);
		// 관심 줄은 변화 줄뿐이라(관심 줄 고르기 — report-R17) 덜어 내는 단계가 없다
		assertThat(interests(after).userStorylines()).hasSize(3);
		assertThat(after.user()).isEqualTo(input.user());
		assertThat(after.period()).isEqualTo(input.period());
		assertThat(after.zone()).isEqualTo(input.zone());
		assertThat(after.statsSnapshot()).isEqualTo(input.statsSnapshot());
	}

	@Test
	void fitsTheLargestMaterialWithinTheMonthlyBudget() {
		// 추적한 사용자 스토리라인 100(사건 줄 10 · 정리 상한) · 사용자 스토리라인 관심 줄 300 · 직전 글 일곱(최대 꼴)
		List<UserStorylineMaterial> userStorylines = LongStream.rangeClosed(1, 100)
			.mapToObj(head -> {
				UserStorylineMaterial userStoryline = userStorylineWithEvents(head, 10, 50);
				return new UserStorylineMaterial(userStoryline.head(), userStoryline.tops(),
						new UserStorylineSummary("바".repeat(120), "사".repeat(1_200), "아".repeat(800), UPDATED), userStoryline.headState(),
						false, userStoryline.articles(), userStoryline.events());
			})
			.toList();
		List<UserStorylineInterestLine> lines = LongStream.rangeClosed(1, 300)
			.mapToObj(head -> new UserStorylineInterestLine(new StorylineId(head),
					new UserStorylineSummary("바".repeat(120), "사".repeat(1_200), "아".repeat(800), UPDATED), 0.1, 0.6,
					InterestShift.ENTERED))
			.toList();
		List<PriorReport> priors = LongStream.rangeClosed(1, 7)
			.mapToObj(id -> new PriorReport(new ReportId(id), ReportPeriodKind.WEEK, Instant.parse("2026-11-09T00:00:00Z"),
					Instant.parse("2026-11-16T00:00:00Z"), "자".repeat(1_500),
					List.of(new PriorSection(ReportSectionKind.STORYLINES, "차".repeat(200), "카".repeat(3_000)),
							new PriorSection(ReportSectionKind.COVERAGE, "차".repeat(200), "카".repeat(3_000)),
							new PriorSection(ReportSectionKind.INTERESTS, "차".repeat(200), "카".repeat(3_000)))))
			.toList();
		ReportInput input = input(List.of(new StorylinesSection(userStorylines), coverage(),
				new InterestsSection(List.of(), lines, List.of())), priors);

		FittedInput fitted = ReportInputFit.fit(input, 112_000);

		assertThat(fitted.overBudget()).isFalse();
		assertThat(ReportInputFit.tokens(fitted.input())).isLessThanOrEqualTo(112_000);
		assertThat(fitted.trimmed().priorsCut()).isEqualTo(7);
	}

	@Test
	void refusesANegativeBudget() {
		ReportInput input = ReportFixtures.input(coverage());

		assertThatThrownBy(() -> ReportInputFit.fit(input, -1)).isInstanceOf(IllegalArgumentException.class);
	}

	private static ReportInput input(List<InputSection> sections, List<PriorReport> priors) {
		return new ReportInput(ReportFixtures.USER, ReportFixtures.PERIOD, ReportFixtures.ZONE, sections, priors,
				ReportFixtures.SNAPSHOT, null);
	}

	private static PriorReport prior(long id, String body) {
		return new PriorReport(new ReportId(id), ReportPeriodKind.WEEK, Instant.parse("2026-11-09T00:00:00Z"),
				Instant.parse("2026-11-16T00:00:00Z"), "지난 개요",
				List.of(new PriorSection(ReportSectionKind.STORYLINES, "지난 제목", body)));
	}

	private static UserStorylineSummary summary(String text) {
		return new UserStorylineSummary("제목", text, "최근", UPDATED);
	}

	private static UserStorylineMaterial trackedUserStoryline(long head, long articles, UserStorylineSummary summary) {
		return new UserStorylineMaterial(new StorylineId(head), Set.of(new StorylineId(head)), summary, StorylineState.ACTIVE,
				false, new Count(articles, null), List.of());
	}

	/** 사건 줄이 {@code events} 개인 사용자 스토리라인 — 사건 ID 는 헤드 × 100 + 순번, 기간 안 기사 수는 내림차순 */
	private static UserStorylineMaterial userStorylineWithEvents(long head, int events, int titleLength) {
		List<EventLine> lines = IntStream.range(0, events)
			.mapToObj(i -> new EventLine(new EventId(head * 100 + i), "제목" + "타".repeat(titleLength),
					LocalDate.of(2026, 11, 17), events - i, true))
			.toList();
		return new UserStorylineMaterial(new StorylineId(head), Set.of(new StorylineId(head)), null, StorylineState.ACTIVE, false,
				new Count(10, null), lines);
	}

	private static StorylinesSection storylines(ReportInput input) {
		return (StorylinesSection) input.sections()
			.stream()
			.filter(section -> section.kind() == ReportSectionKind.STORYLINES)
			.findFirst()
			.orElseThrow();
	}

	private static InterestsSection interests(ReportInput input) {
		return (InterestsSection) input.sections()
			.stream()
			.filter(section -> section.kind() == ReportSectionKind.INTERESTS)
			.findFirst()
			.orElseThrow();
	}

}
