package net.yozmnews.report.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleHeadline;
import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.InterestHistory;
import net.yozmnews.digest.InterestPoint;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.event.EventTally;
import net.yozmnews.event.EventView;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportDirectory;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportSectionView;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.ReportView;
import net.yozmnews.report.domain.input.Count;
import net.yozmnews.report.domain.input.InputSection;
import net.yozmnews.report.domain.input.PriorReport;
import net.yozmnews.report.domain.input.PriorSection;
import net.yozmnews.report.domain.input.ReportInput;
import net.yozmnews.report.domain.input.UserStorylineMaterial;
import net.yozmnews.report.domain.input.UserStorylineSummary;
import net.yozmnews.report.domain.material.CoverageTrend;
import net.yozmnews.report.domain.material.EventLines;
import net.yozmnews.report.domain.material.InterestLines;
import net.yozmnews.report.domain.material.PreviousMatch;
import net.yozmnews.report.domain.material.ReportSections;
import net.yozmnews.report.domain.material.ReportSnapshot;
import net.yozmnews.report.domain.material.ReportSnapshot.SnapshotUserStoryline;
import net.yozmnews.report.domain.material.ReportSnapshot.SnapshotValues;
import net.yozmnews.report.domain.material.TrackedUserStorylines;
import net.yozmnews.report.domain.report.ReportPeriod;
import net.yozmnews.report.domain.view.ReportRow;
import net.yozmnews.report.domain.view.ReportViewRepository;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineEvent;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineView;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.DigestInterests;
import net.yozmnews.user.DigestInterestsDirectory;
import net.yozmnews.user.StorylineSubscriptionChange;
import net.yozmnews.user.StorylineSubscriptionDirectory;
import net.yozmnews.user.UserStorylineDirectory;
import net.yozmnews.user.UserStorylineId;
import net.yozmnews.user.UserStorylineSummaries;
import net.yozmnews.user.UserStorylineSummaryView;
import net.yozmnews.user.UserStorylineView;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 재료 조립 — (사용자, 리포트 기간)의 생성 입력을 다른 모듈의 공개 조회로 모은다 · docs/specs/report.md §4 · report-R14 ~ report-R21
 * <p>
 * 트랜잭션 없이 조회를 차례로 부른다 — 한 스냅숏이 아니다. 판단(갈래 조건 · 판정 · 줄 고르기 · 직전 잇기 · 스냅숏 꼴)은 {@code domain/material}
 * 이 한다. 조회들의 태그 체계 버전이 어긋나면 처음부터 다시 하고 {@link #ATTEMPTS} 번 모두 어긋나면 그친다(report-R21). 역할 애너테이션 없음.
 */
@Component
public class ReportMaterialCollection {

	/** 재료 조립 한 번의 키 버전 대조 시도 수 — 정본. 다이제스트의 시도 수와 값이 같아도 다른 지식이다(report-R21) */
	static final int ATTEMPTS = 2;

	private final TagSetDirectory tagSets;

	private final DigestInterestsDirectory interests;

	private final UserStorylineDirectory userStorylines;

	private final UserStorylineSummaries summaries;

	private final StorylineSubscriptionDirectory subscriptions;

	private final StorylineDirectory storylines;

	private final EventDirectory events;

	private final ArticleDirectory articles;

	private final InterestHistory history;

	private final ReportViewRepository views;

	private final ReportDirectory reports;

	public ReportMaterialCollection(TagSetDirectory tagSets, DigestInterestsDirectory interests, UserStorylineDirectory userStorylines, UserStorylineSummaries summaries,
			StorylineSubscriptionDirectory subscriptions, StorylineDirectory storylines, EventDirectory events,
			ArticleDirectory articles, InterestHistory history, ReportViewRepository views, ReportDirectory reports) {
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
		this.interests = Objects.requireNonNull(interests, "interests");
		this.userStorylines = Objects.requireNonNull(userStorylines, "userStorylines");
		this.summaries = Objects.requireNonNull(summaries, "summaries");
		this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
		this.storylines = Objects.requireNonNull(storylines, "storylines");
		this.events = Objects.requireNonNull(events, "events");
		this.articles = Objects.requireNonNull(articles, "articles");
		this.history = Objects.requireNonNull(history, "history");
		this.views = Objects.requireNonNull(views, "views");
		this.reports = Objects.requireNonNull(reports, "reports");
	}

	/**
	 * @param weeklyReports 월간 리포트에 실을 주간 리포트 — 부르는 쪽이 고른다(주간이면 빈 목록). 그 사용자의 {@code READY} 인 것만 싣는다
	 * @return 계정이 없으면 빈 값
	 * @throws IllegalArgumentException 기간이 집계 기간의 상한을 넘을 때
	 * @throws IllegalStateException 태그 체계 버전이 {@link #ATTEMPTS} 번 모두 어긋날 때
	 */
	public Optional<ReportInput> collect(UserId user, ReportPeriod period, List<ReportId> weeklyReports) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(period, "period");
		Objects.requireNonNull(weeklyReports, "weeklyReports");
		EventPeriod eventPeriod = EventPeriod.of(period.start(), period.end())
			.orElseThrow(() -> new IllegalArgumentException("리포트 기간이 집계 기간의 상한을 넘는다: " + period));
		for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
			Attempt result = attempt(user, period, weeklyReports, eventPeriod);
			if (result instanceof Attempt.Done done) {
				return done.input();
			}
		}
		throw new IllegalStateException("재료의 태그 체계 버전이 " + ATTEMPTS + " 번 어긋났다: 사용자 " + user.value());
	}

	private sealed interface Attempt {

		record Done(Optional<ReportInput> input) implements Attempt {
		}

		record Mismatched() implements Attempt {
		}

	}

	private Attempt attempt(UserId user, ReportPeriod period, List<ReportId> weeklyReports, EventPeriod eventPeriod) {
		TagSetId before = this.tagSets.activeTagSet().id();
		Optional<DigestInterests> found = this.interests.interestsOf(user);
		if (found.isEmpty()) {
			return new Attempt.Done(Optional.empty());
		}
		DigestInterests settings = found.get();
		if (!settings.tagSet().equals(before)) {
			return new Attempt.Mismatched();
		}

		// 추적한 사용자 스토리라인 — 추적 중인 사용자 스토리라인(report-R16)
		List<UserStorylineView> all = this.userStorylines.of(user, period.start(), period.end());
		Map<UserStorylineId, UserStorylineSummaryView> shown = new LinkedHashMap<>();
		this.summaries.shown(user).forEach(summary -> shown.put(summary.id(), summary));
		List<UserStorylineView> tracked = all.stream()
			.filter(UserStorylineView::tracked)
			.sorted(Comparator.comparingLong(userStoryline -> userStoryline.head().value()))
			.toList();
		List<UserStorylineMaterial> userStorylines = userStorylines(user, period, eventPeriod, settings, tracked, shown);

		// 관심 변화 — 시작 · 끝 두 점(report-R17)
		Optional<List<InterestPoint>> points = this.history.at(user,
				List.of(period.start(), period.start(), period.end()));
		if (points.isEmpty()) {
			return new Attempt.Done(Optional.empty());
		}
		if (!this.tagSets.activeTagSet().id().equals(before)) {
			return new Attempt.Mismatched();
		}
		List<InterestLines.UserStorylineRef> refs = userStorylineRefs(all, shown);
		InterestLines.Lines interestLines = InterestLines.of(points.get().get(0), points.get().get(1), refs,
				this.tagSets.names(before));

		// 직전 리포트 — 그 스냅숏에서 1:1 로 이은 직전 값(report-R18)은 견주는 기간 종류만(report-R15)
		Optional<ReportRow> previous = this.views.previous(user, period.kind(), period.start());
		Optional<SnapshotValues> values = CoverageTrend.compares(period.kind())
				? previous.flatMap(row -> ReportSnapshot.read(row.statsSnapshot())) : Optional.empty();
		Map<StorylineId, Long> previousUserStorylines = Map.of();
		if (values.isPresent()) {
			previousUserStorylines = PreviousMatch.userStorylines(values.get().userStorylines(), resolvedTops(values.get().userStorylines()),
					currentUserStorylines(userStorylines));
		}
		Map<StorylineId, Long> carriedUserStorylines = previousUserStorylines;
		userStorylines = userStorylines.stream()
			.map(userStoryline -> new UserStorylineMaterial(userStoryline.head(), userStoryline.tops(), userStoryline.summary(), userStoryline.headState(),
					userStoryline.trackedInPeriod(), new Count(userStoryline.articles().current(), carriedUserStorylines.get(userStoryline.head())),
					userStoryline.events()))
			.toList();

		// 직전 리포트 글 — 직전 리포트와 넘긴 주간 리포트 가운데 READY 인 보이는 글(report-R20)
		List<PriorReport> priors = new ArrayList<>();
		previous.filter(row -> row.status() == ReportStatus.READY)
			.flatMap(row -> prior(user, row.id()))
			.ifPresent(priors::add);
		weeklyReports.forEach(id -> prior(user, id).ifPresent(priors::add));

		if (!this.tagSets.activeTagSet().id().equals(before)) {
			return new Attempt.Mismatched();
		}
		List<InputSection> sections = ReportSections.of(userStorylines, interestLines);
		return new Attempt.Done(Optional.of(new ReportInput(user, period, settings.timezone(), sections, priors,
				ReportSnapshot.write(period, before, sections), previous.map(ReportRow::id).orElse(null))));
	}

	/** 사용자 스토리라인 재료 — 직전 값은 나중에 잇는다 */
	private List<UserStorylineMaterial> userStorylines(UserId user, ReportPeriod period, EventPeriod eventPeriod, DigestInterests settings,
			List<UserStorylineView> tracked, Map<UserStorylineId, UserStorylineSummaryView> shown) {
		if (tracked.isEmpty()) {
			return List.of();
		}
		Map<StorylineId, StorylineView> heads = this.storylines
			.findAll(tracked.stream().map(UserStorylineView::head).toList());
		Map<StorylineId, Set<StorylineId>> groups = new LinkedHashMap<>();
		tracked.forEach(userStoryline -> groups.put(userStoryline.head(), userStoryline.tops()));
		Map<StorylineId, EventTally> tallies = this.storylines.tallies(groups, eventPeriod);

		// 사용자 스토리라인마다 꼭대기들의 닫은 사건 — 사건마다 기간 안 기사 수
		Map<StorylineId, Map<EventId, EventView>> eventsOf = new LinkedHashMap<>();
		Map<EventId, List<EventId>> single = new LinkedHashMap<>();
		for (UserStorylineView userStoryline : tracked) {
			Map<EventId, EventView> closed = new LinkedHashMap<>();
			for (StorylineId top : userStoryline.tops()) {
				for (StorylineEvent event : this.storylines.events(top)) {
					closed.putIfAbsent(event.event().id(), event.event());
					single.putIfAbsent(event.event().id(), List.of(event.event().id()));
				}
			}
			eventsOf.put(userStoryline.head(), closed);
		}
		Map<EventId, EventTally> perEvent = single.isEmpty() ? Map.of() : this.events.tallies(single, eventPeriod);
		Set<EventId> inPeriod = new HashSet<>();
		perEvent.forEach((event, tally) -> {
			if (tally.articles() > 0) {
				inPeriod.add(event);
			}
		});
		Map<EventId, ArticleId> representatives = inPeriod.isEmpty() ? Map.of()
				: this.events.representativesOfAll(inPeriod);
		Map<ArticleId, ArticleHeadline> headlines = representatives.isEmpty() ? Map.of()
				: this.articles.headlines(new HashSet<>(representatives.values()));

		// 기간 안에 추적을 더한 사용자 스토리라인(report-R16)
		List<StorylineSubscriptionChange> changes = this.subscriptions.changes(user, period.start(), period.end());
		Set<StorylineId> currents = new LinkedHashSet<>();
		changes.forEach(change -> {
			if (change.current() != null) {
				currents.add(change.current());
			}
		});
		Map<StorylineId, StorylineId> topsOfChanges = currents.isEmpty() ? Map.of()
				: this.storylines.topsOfStorylines(currents);
		Set<StorylineId> started = TrackedUserStorylines.startedIn(changes, topsOfChanges, groups);

		List<UserStorylineMaterial> materials = new ArrayList<>();
		for (UserStorylineView userStoryline : tracked) {
			StorylineView head = heads.get(userStoryline.head());
			if (head == null) {
				// 그새 지운 스토리라인 — 다음 맞추기가 사용자 스토리라인에서 뺀다
				continue;
			}
			List<EventLines.EventFact> facts = eventsOf.get(userStoryline.head())
				.values()
				.stream()
				.map(event -> new EventLines.EventFact(event.id(), event.firstArticleAt(), articlesOf(perEvent, event.id()),
						title(representatives, headlines, event.id())))
				.toList();
			materials.add(new UserStorylineMaterial(userStoryline.head(), userStoryline.tops(), summaryOf(userStoryline, shown), head.state(),
					started.contains(userStoryline.head()), new Count(tallies.get(userStoryline.head()).articles(), null),
					EventLines.pick(facts, period, settings.timezone())));
		}
		return List.copyOf(materials);
	}

	private static int articlesOf(Map<EventId, EventTally> perEvent, EventId event) {
		EventTally tally = perEvent.get(event);
		return (tally == null) ? 0 : tally.articles();
	}

	private static @Nullable String title(Map<EventId, ArticleId> representatives,
			Map<ArticleId, ArticleHeadline> headlines, EventId event) {
		ArticleId representative = representatives.get(event);
		ArticleHeadline headline = (representative == null) ? null : headlines.get(representative);
		return (headline == null) ? null : headline.title();
	}

	private static @Nullable UserStorylineSummary summaryOf(UserStorylineView userStoryline,
			Map<UserStorylineId, UserStorylineSummaryView> shown) {
		UserStorylineSummaryView summary = (userStoryline.id() == null) ? null : shown.get(userStoryline.id());
		return (summary == null) ? null : summary(summary);
	}

	private static UserStorylineSummary summary(UserStorylineSummaryView summary) {
		return new UserStorylineSummary(summary.title(), summary.summary(), summary.recent(), summary.updatedAt());
	}

	/** 관심 줄의 사용자 스토리라인 — 사용자 스토리라인 전부와 정리된 사용자 스토리라인의 합집합, 같은 ID 면 사용자 스토리라인의 헤드(report-R17) */
	private static List<InterestLines.UserStorylineRef> userStorylineRefs(List<UserStorylineView> all,
			Map<UserStorylineId, UserStorylineSummaryView> shown) {
		List<InterestLines.UserStorylineRef> refs = new ArrayList<>();
		Set<UserStorylineId> seen = new HashSet<>();
		for (UserStorylineView userStoryline : all) {
			if (userStoryline.id() != null) {
				seen.add(userStoryline.id());
			}
			refs.add(new InterestLines.UserStorylineRef(userStoryline.head(), summaryOf(userStoryline, shown)));
		}
		shown.values().forEach(summary -> {
			if (!seen.contains(summary.id())) {
				refs.add(new InterestLines.UserStorylineRef(summary.head(), summary(summary)));
			}
		});
		return refs;
	}

	private Map<StorylineId, StorylineId> resolvedTops(List<SnapshotUserStoryline> userStorylines) {
		Set<StorylineId> tops = new LinkedHashSet<>();
		userStorylines.forEach(userStoryline -> tops.addAll(userStoryline.tops()));
		return tops.isEmpty() ? Map.of() : this.storylines.topsOfStorylines(tops);
	}

	private static Map<StorylineId, Set<StorylineId>> currentUserStorylines(Collection<UserStorylineMaterial> userStorylines) {
		Map<StorylineId, Set<StorylineId>> current = new LinkedHashMap<>();
		userStorylines.forEach(userStoryline -> current.put(userStoryline.head(), userStoryline.tops()));
		return current;
	}

	/** 리포트 한 편의 보이는 글 — 그 사용자의 READY 리포트만(report-R20) */
	private Optional<PriorReport> prior(UserId user, ReportId id) {
		return this.reports.find(user, id)
			.filter(view -> view.summary().status() == ReportStatus.READY)
			.map(ReportMaterialCollection::prior);
	}

	private static PriorReport prior(ReportView view) {
		List<PriorSection> sections = new ArrayList<>();
		for (ReportSectionView section : view.sections()) {
			sections.add(new PriorSection(section.kind(), section.title(), section.body()));
		}
		return new PriorReport(view.summary().id(), view.summary().periodKind(), view.summary().periodStart(),
				view.summary().periodEnd(), sections);
	}

}
