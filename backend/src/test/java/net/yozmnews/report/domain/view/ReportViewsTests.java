package net.yozmnews.report.domain.view;

import static net.yozmnews.report.ReportFixtures.event;
import static net.yozmnews.report.ReportFixtures.storyline;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import net.yozmnews.event.EventId;
import net.yozmnews.report.InterestShift;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPage;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.ReportSectionStats;
import net.yozmnews.report.ReportSectionView;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.ReportSummary;
import net.yozmnews.report.ReportTrend;
import net.yozmnews.report.ReportView;
import net.yozmnews.storyline.StorylineId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * 조회의 풀기 · 숨기기 · 숫자 · 쪽 — 합친 것은 남는 것으로, 지운 것은 빼고, 인용을 모두 잃은 섹션 · 개요의 숨김과 그 수, 보이는 갈래
 * 섹션의 숫자와 사용자 스토리라인 줄의 풀기. 기대 숫자는 손으로 쓴 스냅숏 리터럴에서 손으로 옮긴 값이다.
 * <p>
 * 지키는 규칙: report-R11 · report-R12 · report-R25 · report-R26
 */
class ReportViewsTests {

	private static final StoredSection OVERVIEW = new StoredSection(ReportSectionKind.OVERVIEW, null, "개요", List.of());

	/** 사건 902 는 그대로, 903 은 904 에 합쳐졌고, 905 는 지웠다. 스토리라인 31 은 그대로, 32 는 33 에 합쳐졌고, 34 는 지웠다 */
	private static final Map<EventId, EventId> EVENTS = Map.of(new EventId(902), new EventId(902), new EventId(903),
			new EventId(904), new EventId(904), new EventId(904));

	private static final Map<StorylineId, StorylineId> STORYLINES = Map.of(new StorylineId(31), new StorylineId(31),
			new StorylineId(32), new StorylineId(33));

	/** 스냅숏 사용자 스토리라인 줄의 헤드: 31 은 그대로, 32 는 33 에 합쳐졌고, 33 은 그대로, 34 는 지웠다 */
	private static final Map<StorylineId, StorylineId> HEADS = Map.of(new StorylineId(31), new StorylineId(31),
			new StorylineId(32), new StorylineId(33), new StorylineId(33), new StorylineId(33));

	/** 갈래 셋이 있는 스냅숏 — 손으로 쓴 꼴(report-R19) */
	private static final String SNAPSHOT = """
			{"version":2,"tagSet":5,
			 "period":{"kind":"WEEK","start":"2026-11-16T00:00:00Z","end":"2026-11-23T00:00:00Z"},
			 "storylines":{"basis":"PUBLISHED","userStorylines":[
			   {"head":31,"tops":[31],"articles":12,"previous":7,"trend":"ROSE","trackedInPeriod":false},
			   {"head":32,"tops":[32],"articles":5,"previous":null,"trend":"NO_COMPARISON","trackedInPeriod":true},
			   {"head":33,"tops":[33],"articles":9,"previous":9,"trend":"STEADY","trackedInPeriod":false},
			   {"head":34,"tops":[34],"articles":3,"previous":1,"trend":"STEADY","trackedInPeriod":false}]},
			 "coverage":{"basis":"FIRST_EVALUATED","topics":[
			   {"key":"economy.finance","name":"금융","articles":40,"previous":12,"trend":"ROSE"}]},
			 "interests":{
			   "topics":[{"key":"economy.finance","name":"금융","start":0.42,"end":null,"shift":"LEFT"}],
			   "userStorylines":[{"head":34,"start":null,"end":0.3,"shift":"ENTERED"},
			            {"head":32,"start":0.2,"end":0.6,"shift":"NONE"}]}}""";

	@Test
	void resolvesMergedTargetsLeavesOutRemovedOnesAndKeepsOneOfTargetsThatBecameTheSame() {
		List<ReportSectionView> sections = view(List.of(OVERVIEW, new StoredSection(ReportSectionKind.STORYLINES, "흐름",
				"본문", List.of(storyline(32), event(905), event(903), event(904), storyline(34), storyline(31)))));

		assertThat(sections).containsExactly(new ReportSectionView(ReportSectionKind.OVERVIEW, null, "개요", List.of(), null),
				new ReportSectionView(ReportSectionKind.STORYLINES, "흐름", "본문",
						List.of(storyline(33), event(904), storyline(31)), null));
	}

	@Test
	void hidesASectionThatLostEveryCitationAndShowsOneThatNeverHadAny() {
		List<ReportSectionView> sections = view(List.of(OVERVIEW,
				new StoredSection(ReportSectionKind.STORYLINES, "흐름", "본문 하나", List.of(event(905), storyline(34))),
				new StoredSection(ReportSectionKind.INTERESTS, "관심", "본문 둘", List.of()),
				new StoredSection(ReportSectionKind.COVERAGE, "보도량", "본문 셋", List.of(event(905), event(902)))));

		assertThat(sections).extracting(ReportSectionView::kind)
			.containsExactly(ReportSectionKind.OVERVIEW, ReportSectionKind.INTERESTS, ReportSectionKind.COVERAGE);
		assertThat(sections.get(2).citations()).containsExactly(event(902));
	}

	@Test
	void hidesTheOverviewOnlyWhenEveryKindSectionWasHidden() {
		List<ReportSectionView> allHidden = view(List.of(OVERVIEW,
				new StoredSection(ReportSectionKind.STORYLINES, "흐름", "본문", List.of(event(905))),
				new StoredSection(ReportSectionKind.COVERAGE, "보도량", "본문", List.of(storyline(34)))));
		List<ReportSectionView> oneLeft = view(List.of(OVERVIEW,
				new StoredSection(ReportSectionKind.STORYLINES, "흐름", "본문", List.of(event(905))),
				new StoredSection(ReportSectionKind.COVERAGE, "보도량", "본문", List.of(storyline(31)))));

		assertThat(allHidden).isEmpty();
		assertThat(oneLeft).extracting(ReportSectionView::kind)
			.containsExactly(ReportSectionKind.OVERVIEW, ReportSectionKind.COVERAGE);
	}

	@Test
	void showsTheOverviewOfAReadyReportWithoutKindSections() {
		assertThat(view(List.of(OVERVIEW)))
			.containsExactly(new ReportSectionView(ReportSectionKind.OVERVIEW, null, "개요", List.of(), null));
	}

	@Test
	void aReportThatIsNotReadyHasNoSections() {
		for (ReportStatus status : List.of(ReportStatus.SCHEDULED, ReportStatus.RUNNING, ReportStatus.EMPTY,
				ReportStatus.FAILED)) {
			ReportView view = render(row(1, status, SNAPSHOT), List.of(OVERVIEW,
					new StoredSection(ReportSectionKind.STORYLINES, "흐름", "본문", List.of(storyline(34)))), EVENTS,
					STORYLINES);
			assertThat(view.sections()).as(status.name()).isEmpty();
			assertThat(view.hiddenSections()).as(status.name()).isZero();
		}
	}

	@Test
	void showsEachVisibleKindSectionWithItsNumbersAndCountsTheHiddenOnes() {
		ReportView view = render(row(1, ReportStatus.READY, SNAPSHOT), List.of(OVERVIEW,
				new StoredSection(ReportSectionKind.STORYLINES, "흐름", "본문 하나", List.of(storyline(32))),
				new StoredSection(ReportSectionKind.COVERAGE, "보도량", "본문 둘", List.of()),
				new StoredSection(ReportSectionKind.INTERESTS, "관심", "본문 셋", List.of(storyline(34)))), EVENTS, HEADS);

		assertThat(view.hiddenSections()).isEqualTo(1);
		assertThat(view.sections()).containsExactly(
				new ReportSectionView(ReportSectionKind.OVERVIEW, null, "개요", List.of(), null),
				new ReportSectionView(ReportSectionKind.STORYLINES, "흐름", "본문 하나", List.of(storyline(33)),
						new ReportSectionStats.UserStorylines(List.of(
								new ReportSectionStats.UserStorylineCount(new StorylineId(31), 12, 7L, ReportTrend.ROSE, false),
								new ReportSectionStats.UserStorylineCount(new StorylineId(33), 5, null, ReportTrend.NO_COMPARISON,
										true)))),
				new ReportSectionView(ReportSectionKind.COVERAGE, "보도량", "본문 둘", List.of(),
						new ReportSectionStats.Coverage(
								List.of(new ReportSectionStats.TopicCount("금융", 40, 12L, ReportTrend.ROSE)))));
	}

	@Test
	void aVisibleInterestsSectionHasTopicLinesAndTheUserStorylineLinesThatStillExist() {
		ReportView view = render(row(1, ReportStatus.READY, SNAPSHOT), List.of(OVERVIEW,
				new StoredSection(ReportSectionKind.INTERESTS, "관심", "본문", List.of(storyline(31)))), EVENTS, HEADS);

		assertThat(view.hiddenSections()).isZero();
		assertThat(view.sections().get(1).stats()).isEqualTo(new ReportSectionStats.Interests(
				List.of(new ReportSectionStats.TopicLevel("금융", 0.42, null, InterestShift.LEFT)),
				List.of(new ReportSectionStats.UserStorylineLevel(new StorylineId(33), 0.2, 0.6, InterestShift.NONE))));
	}

	@Test
	void aSectionWhoseKindIsMissingFromTheSnapshotOrAnUnreadableSnapshotHasNoNumbers() {
		String withoutCoverage = """
				{"version":2,"tagSet":5,"period":{"kind":"WEEK","start":"2026-11-16T00:00:00Z","end":"2026-11-23T00:00:00Z"}}""";
		String unknownVersion = SNAPSHOT.replace("\"version\":2", "\"version\":3");
		List<StoredSection> stored = List.of(OVERVIEW,
				new StoredSection(ReportSectionKind.COVERAGE, "보도량", "본문", List.of()),
				new StoredSection(ReportSectionKind.STORYLINES, "흐름", "본문", List.of(storyline(31))));

		for (String snapshot : List.of(withoutCoverage, unknownVersion)) {
			ReportView view = render(row(1, ReportStatus.READY, snapshot), stored, EVENTS, HEADS);
			assertThat(view.sections()).extracting(ReportSectionView::stats).containsOnlyNulls();
			assertThat(view.sections()).extracting(ReportSectionView::body).containsExactly("개요", "본문", "본문");
		}
	}

	@Test
	void snapshotHeadsAreTheUserStorylineHeadsOfAReadyReport() {
		assertThat(ReportViews.snapshotHeads(ReportViews.snapshotOf(row(1, ReportStatus.READY, SNAPSHOT)))).containsExactlyInAnyOrder(
				new StorylineId(31), new StorylineId(32), new StorylineId(33), new StorylineId(34));
		assertThat(ReportViews.snapshotHeads(ReportViews.snapshotOf(row(1, ReportStatus.FAILED, SNAPSHOT)))).isEmpty();
		assertThat(ReportViews.snapshotHeads(ReportViews.snapshotOf(row(1, ReportStatus.READY, null)))).isEmpty();
	}

	@Test
	void pagesBySizeAndPointsTheNextPageAtTheLastId() {
		List<ReportRow> three = List.of(row(9, ReportStatus.READY, null), row(7, ReportStatus.EMPTY, null),
				row(4, ReportStatus.READY, null));

		ReportPage first = ReportViews.page(three, 2, ZoneOffset.UTC);
		ReportPage last = ReportViews.page(three.subList(0, 2), 2, ZoneOffset.UTC);

		assertThat(first.reports()).extracting(ReportSummary::id).containsExactly(new ReportId(9), new ReportId(7));
		assertThat(first.next()).isEqualTo(new ReportId(7));
		assertThat(last.reports()).hasSize(2);
		assertThat(last.next()).isNull();
		assertThat(ReportViews.page(List.of(), 2, ZoneOffset.UTC)).isEqualTo(new ReportPage(List.of(), null));
	}

	@Test
	void writesThePeriodDaysInTheZoneTheRowRecordedOrInTheCurrentZoneBeforeStart() {
		// 서울 자정에 시작해 서울 자정에 끝나는 주 — UTC 로는 전날 15 시
		ReportRow started = new ReportRow(new ReportId(5), ReportPeriodKind.WEEK, Instant.parse("2026-11-15T15:00:00Z"),
				Instant.parse("2026-11-22T15:00:00Z"), ReportStatus.EMPTY, null, Instant.parse("2026-11-22T15:00:00Z"),
				Instant.parse("2026-11-22T15:01:00Z"), ZoneId.of("Asia/Seoul"), null, "openai", "gpt-5.5-mini", null, null,
				"{}");
		ReportRow scheduled = new ReportRow(new ReportId(6), ReportPeriodKind.WEEK,
				Instant.parse("2026-11-22T15:00:00Z"), Instant.parse("2026-11-29T15:00:00Z"), ReportStatus.SCHEDULED,
				null, Instant.parse("2026-11-29T15:00:00Z"), null, null, null, null, null, null, null, null);

		ReportSummary startedSummary = ReportViews.summary(started, ZoneOffset.UTC);
		ReportSummary scheduledInSeoul = ReportViews.summary(scheduled, ZoneId.of("Asia/Seoul"));
		ReportSummary scheduledInUtc = ReportViews.summary(scheduled, ZoneOffset.UTC);

		assertThat(startedSummary.firstDay()).isEqualTo(LocalDate.parse("2026-11-16"));
		assertThat(startedSummary.lastDay()).isEqualTo(LocalDate.parse("2026-11-22"));
		assertThat(scheduledInSeoul.firstDay()).isEqualTo(LocalDate.parse("2026-11-23"));
		assertThat(scheduledInSeoul.lastDay()).isEqualTo(LocalDate.parse("2026-11-29"));
		assertThat(scheduledInUtc.firstDay()).isEqualTo(LocalDate.parse("2026-11-22"));
		assertThat(scheduledInUtc.lastDay()).isEqualTo(LocalDate.parse("2026-11-29"));
	}

	@Test
	void carriesThePreviousReportAsASummaryWhenThereIsOne() {
		ReportRow previous = row(3, ReportStatus.READY, null);

		assertThat(ReportViews.view(row(4, ReportStatus.READY, null), List.of(OVERVIEW), null, EVENTS, STORYLINES,
				previous, ZoneOffset.UTC).previous()).extracting(ReportSummary::id).isEqualTo(new ReportId(3));
		assertThat(render(row(4, ReportStatus.READY, null), List.of(OVERVIEW), EVENTS, STORYLINES).previous()).isNull();
	}

	private static List<ReportSectionView> view(List<StoredSection> stored) {
		return render(row(1, ReportStatus.READY, null), stored, EVENTS, STORYLINES).sections();
	}

	/** 조회와 같은 순서 — 스냅숏을 한 번 읽어 넘긴다. 직전 리포트 없음, 대체 시간대 UTC */
	private static ReportView render(ReportRow row, List<StoredSection> stored, Map<EventId, EventId> events,
			Map<StorylineId, StorylineId> storylines) {
		return ReportViews.view(row, stored, ReportViews.snapshotOf(row), events, storylines, null, ZoneOffset.UTC);
	}

	/** 시작한 행 — 시간대 UTC 를 적었다 */
	private static ReportRow row(long id, ReportStatus status, @Nullable String snapshot) {
		return new ReportRow(new ReportId(id), ReportPeriodKind.WEEK, Instant.parse("2026-11-16T00:00:00Z"),
				Instant.parse("2026-11-23T00:00:00Z"), status, null, Instant.parse("2026-11-23T00:00:00Z"), null,
				ZoneOffset.UTC, null, null, null, null, null, snapshot);
	}

}
