package net.yozmnews.report.domain.view;

import static net.yozmnews.report.ReportFixtures.event;
import static net.yozmnews.report.ReportFixtures.storyline;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.event.EventId;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPage;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.ReportSectionView;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.ReportSummary;
import net.yozmnews.storyline.StorylineId;
import org.junit.jupiter.api.Test;

/**
 * 조회의 풀기 · 숨기기 · 쪽 — 합친 것은 남는 것으로, 지운 것은 빼고, 인용을 모두 잃은 섹션 · 개요의 숨김.
 * <p>
 * 지키는 규칙: report-R11 · report-R12
 */
class ReportViewsTests {

	private static final StoredSection OVERVIEW = new StoredSection(ReportSectionKind.OVERVIEW, null, "개요", List.of());

	/** 사건 902 는 그대로, 903 은 904 에 합쳐졌고, 905 는 지웠다. 스토리라인 31 은 그대로, 32 는 33 에 합쳐졌고, 34 는 지웠다 */
	private static final Map<EventId, EventId> EVENTS = Map.of(new EventId(902), new EventId(902), new EventId(903),
			new EventId(904), new EventId(904), new EventId(904));

	private static final Map<StorylineId, StorylineId> STORYLINES = Map.of(new StorylineId(31), new StorylineId(31),
			new StorylineId(32), new StorylineId(33));

	@Test
	void resolvesMergedTargetsLeavesOutRemovedOnesAndKeepsOneOfTargetsThatBecameTheSame() {
		List<ReportSectionView> sections = view(List.of(OVERVIEW, new StoredSection(ReportSectionKind.STORYLINES, "흐름",
				"본문", List.of(storyline(32), event(905), event(903), event(904), storyline(34), storyline(31)))));

		assertThat(sections).containsExactly(new ReportSectionView(ReportSectionKind.OVERVIEW, null, "개요", List.of()),
				new ReportSectionView(ReportSectionKind.STORYLINES, "흐름", "본문",
						List.of(storyline(33), event(904), storyline(31))));
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
			.containsExactly(new ReportSectionView(ReportSectionKind.OVERVIEW, null, "개요", List.of()));
	}

	@Test
	void aReportThatIsNotReadyHasNoSections() {
		for (ReportStatus status : List.of(ReportStatus.SCHEDULED, ReportStatus.RUNNING, ReportStatus.EMPTY,
				ReportStatus.FAILED)) {
			assertThat(ReportViews.view(row(status), List.of(OVERVIEW), EVENTS, STORYLINES).sections()).as(status.name())
				.isEmpty();
		}
	}

	@Test
	void pagesBySizeAndPointsTheNextPageAtTheLastId() {
		List<ReportRow> three = List.of(row(9, ReportStatus.READY), row(7, ReportStatus.EMPTY), row(4, ReportStatus.READY));

		ReportPage first = ReportViews.page(three, 2);
		ReportPage last = ReportViews.page(three.subList(0, 2), 2);

		assertThat(first.reports()).extracting(ReportSummary::id).containsExactly(new ReportId(9), new ReportId(7));
		assertThat(first.next()).isEqualTo(new ReportId(7));
		assertThat(last.reports()).hasSize(2);
		assertThat(last.next()).isNull();
		assertThat(ReportViews.page(List.of(), 2)).isEqualTo(new ReportPage(List.of(), null));
	}

	private static List<ReportSectionView> view(List<StoredSection> stored) {
		return ReportViews.view(row(ReportStatus.READY), stored, EVENTS, STORYLINES).sections();
	}

	private static ReportRow row(ReportStatus status) {
		return row(1, status);
	}

	private static ReportRow row(long id, ReportStatus status) {
		return new ReportRow(new ReportSummary(new ReportId(id), ReportPeriodKind.WEEK, Instant.parse("2026-11-16T00:00:00Z"),
				Instant.parse("2026-11-23T00:00:00Z"), status, null, Instant.parse("2026-11-23T00:00:00Z"), null), null,
				null, null, null, null);
	}

}
