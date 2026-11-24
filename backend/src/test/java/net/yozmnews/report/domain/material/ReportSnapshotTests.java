package net.yozmnews.report.domain.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.report.InterestShift;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportTrend;
import net.yozmnews.report.domain.input.CoverageSection;
import net.yozmnews.report.domain.input.Count;
import net.yozmnews.report.domain.input.FlowInterestLine;
import net.yozmnews.report.domain.input.FlowMaterial;
import net.yozmnews.report.domain.input.InterestsSection;
import net.yozmnews.report.domain.input.StorylinesSection;
import net.yozmnews.report.domain.input.TopicCoverageLine;
import net.yozmnews.report.domain.input.TopicInterestLine;
import net.yozmnews.report.domain.material.ReportSnapshot.CoverageStats;
import net.yozmnews.report.domain.material.ReportSnapshot.FlowLevelStat;
import net.yozmnews.report.domain.material.ReportSnapshot.FlowStat;
import net.yozmnews.report.domain.material.ReportSnapshot.FlowStats;
import net.yozmnews.report.domain.material.ReportSnapshot.InterestStats;
import net.yozmnews.report.domain.material.ReportSnapshot.SnapshotFlow;
import net.yozmnews.report.domain.material.ReportSnapshot.SnapshotStats;
import net.yozmnews.report.domain.material.ReportSnapshot.SnapshotValues;
import net.yozmnews.report.domain.material.ReportSnapshot.TopicLevelStat;
import net.yozmnews.report.domain.material.ReportSnapshot.TopicStat;
import net.yozmnews.report.domain.report.ReportPeriod;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineState;
import net.yozmnews.tag.TagSetId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 스냅숏 꼴 — 손으로 쓴 JSON 과 견준 쓰기와 읽기(다음 리포트가 견줄 값 · 모든 칸), 모르는 판 · 깨진 문자열 · 꼴이 틀린 칸, 두 보도량의 기간
 * 시각. 지키는 규칙: report-R19
 */
class ReportSnapshotTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final ReportPeriod WEEK = new ReportPeriod(ReportPeriodKind.WEEK,
			Instant.parse("2026-11-16T15:00:00Z"), Instant.parse("2026-11-23T15:00:00Z"));

	@Test
	void writesTheStatisticsOfTheSectionsInTheInputAsTheHandWrittenShape() {
		String written = ReportSnapshot.write(WEEK, new TagSetId(4), List.of(
				new StorylinesSection(List.of(new FlowMaterial(new StorylineId(12),
						Set.of(new StorylineId(15), new StorylineId(12)), null, StorylineState.ACTIVE, false,
						new Count(30, 20L), List.of()))),
				new CoverageSection(List.of(new TopicCoverageLine("economy.finance", "금융", new Count(120, 100L)),
						new TopicCoverageLine("economy.trade", "무역", new Count(4, null)))),
				new InterestsSection(
						List.of(new TopicInterestLine("economy.finance", "금융", 0.31, 0.52, InterestShift.ENTERED),
								new TopicInterestLine("economy.trade", "무역", 0.25, null, InterestShift.LEFT)),
						List.of(new FlowInterestLine(new StorylineId(12), null, 0.4, 0.6, InterestShift.NONE)),
						List.of())));

		assertThat(JSON.readTree(written)).isEqualTo(JSON.readTree("""
				{ "version": 1, "tagSet": 4,
				  "period": { "kind": "WEEK", "start": "2026-11-16T15:00:00Z", "end": "2026-11-23T15:00:00Z" },
				  "storylines": { "basis": "PUBLISHED",
				    "flows": [ { "head": 12, "tops": [12, 15], "articles": 30, "previous": 20, "trend": "ROSE",
				                 "trackedInPeriod": false } ] },
				  "coverage": { "basis": "FIRST_EVALUATED",
				    "topics": [ { "key": "economy.finance", "name": "금융", "articles": 120, "previous": 100, "trend": "STEADY" },
				                { "key": "economy.trade", "name": "무역", "articles": 4, "previous": null,
				                  "trend": "NO_COMPARISON" } ] },
				  "interests": {
				    "topics": [ { "key": "economy.finance", "name": "금융", "start": 0.31, "end": 0.52, "shift": "ENTERED" },
				                { "key": "economy.trade", "name": "무역", "start": 0.25, "end": null, "shift": "LEFT" } ],
				    "flows": [ { "head": 12, "start": 0.4, "end": 0.6, "shift": "NONE" } ] } }"""));
		// 두 보도량은 기간 시각의 종류가 다르다
		assertThat(JSON.readTree(written).get("coverage").get("basis").stringValue()).isEqualTo("FIRST_EVALUATED");
		assertThat(JSON.readTree(written).get("storylines").get("basis").stringValue()).isEqualTo("PUBLISHED");
	}

	@Test
	void writesOnlyTheSectionsInTheInput() {
		assertThat(JSON.readTree(ReportSnapshot.write(WEEK, new TagSetId(4), List.of()))).isEqualTo(JSON.readTree("""
				{ "version": 1, "tagSet": 4,
				  "period": { "kind": "WEEK", "start": "2026-11-16T15:00:00Z", "end": "2026-11-23T15:00:00Z" } }"""));
	}

	@Test
	void readsTheValuesTheNextReportComparesFromAHandWrittenSnapshot() {
		assertThat(ReportSnapshot.read("""
				{ "version": 1, "tagSet": 3,
				  "period": { "kind": "WEEK", "start": "2026-11-09T15:00:00Z", "end": "2026-11-16T15:00:00Z" },
				  "storylines": { "basis": "PUBLISHED",
				    "flows": [ { "head": 12, "tops": [15, 12], "articles": 20, "previous": null, "trend": "NO_COMPARISON",
				                 "trackedInPeriod": true } ] },
				  "coverage": { "basis": "FIRST_EVALUATED",
				    "topics": [ { "key": "economy.finance", "name": "금융", "articles": 100, "previous": null,
				                  "trend": "NO_COMPARISON" } ] } }"""))
			.contains(new SnapshotValues(new TagSetId(3), Map.of("economy.finance", 100L),
					List.of(new SnapshotFlow(new StorylineId(12), Set.of(new StorylineId(12), new StorylineId(15)), 20))));
		// 갈래가 없던 스냅숏은 값이 비었다
		assertThat(ReportSnapshot.read("""
				{ "version": 1, "tagSet": 3, "period": { "kind": "WEEK", "start": "2026-11-09T15:00:00Z",
				  "end": "2026-11-16T15:00:00Z" } }"""))
			.contains(new SnapshotValues(new TagSetId(3), Map.of(), List.of()));
	}

	@Test
	void readsNothingFromAnUnknownVersionOrAnUnreadableText() {
		assertThat(ReportSnapshot.read("{\"version\": 2, \"tagSet\": 3}")).isEmpty();
		assertThat(ReportSnapshot.read("{\"tagSet\": 3}")).isEmpty();
		assertThat(ReportSnapshot.read("{\"coverage\":{\"이번\":12,\"직전\":7}}")).isEmpty();
		assertThat(ReportSnapshot.read("{\"version\": 1, \"tagSet\": 3, \"coverage\": {\"topics\": [{\"key\": 7}]}}"))
			.isEmpty();
		assertThat(ReportSnapshot.read("{")).isEmpty();
		assertThat(ReportSnapshot.read(null)).isEmpty();
	}

	@Test
	void statsReadsEveryFieldOfEachSectionFromAHandWrittenSnapshot() {
		assertThat(ReportSnapshot.stats("""
				{ "version": 1, "tagSet": 4,
				  "period": { "kind": "WEEK", "start": "2026-11-16T15:00:00Z", "end": "2026-11-23T15:00:00Z" },
				  "storylines": { "basis": "PUBLISHED",
				    "flows": [ { "head": 12, "tops": [15, 12], "articles": 30, "previous": 20, "trend": "ROSE",
				                 "trackedInPeriod": false } ] },
				  "coverage": { "basis": "FIRST_EVALUATED",
				    "topics": [ { "key": "economy.trade", "name": "무역", "articles": 4, "previous": null,
				                  "trend": "NO_COMPARISON" } ] },
				  "interests": {
				    "topics": [ { "key": "economy.finance", "name": "금융", "start": 0.25, "end": null, "shift": "LEFT" } ],
				    "flows": [ { "head": 12, "start": null, "end": 0.6, "shift": "ENTERED" } ] } }"""))
			.contains(new SnapshotStats(new TagSetId(4),
					new CoverageStats(
							List.of(new TopicStat("economy.trade", "무역", 4, null, ReportTrend.NO_COMPARISON))),
					new FlowStats(List.of(new FlowStat(new StorylineId(12), Set.of(new StorylineId(12), new StorylineId(15)),
							30, 20L, ReportTrend.ROSE, false))),
					new InterestStats(
							List.of(new TopicLevelStat("economy.finance", "금융", 0.25, null, InterestShift.LEFT)),
							List.of(new FlowLevelStat(new StorylineId(12), null, 0.6, InterestShift.ENTERED)))));
	}

	@Test
	void statsLeavesASectionThatIsNotInTheSnapshotEmpty() {
		assertThat(ReportSnapshot.stats("""
				{ "version": 1, "tagSet": 4,
				  "period": { "kind": "MONTH", "start": "2026-10-31T15:00:00Z", "end": "2026-11-30T15:00:00Z" },
				  "coverage": { "basis": "FIRST_EVALUATED", "topics": [] } }"""))
			.contains(new SnapshotStats(new TagSetId(4), new CoverageStats(List.of()), null, null));
	}

	@Test
	void statsReadsNothingFromAnUnknownVersionOrAFieldOfTheWrongKind() {
		String flow = """
				{ "version": %d, "tagSet": 4, "storylines": { "flows": [ { "head": 12, "tops": [12], "articles": 30,
				  "previous": 20, "trend": "%s", "trackedInPeriod": false } ] } }""";
		assertThat(ReportSnapshot.stats(flow.formatted(1, "ROSE"))).isPresent();
		assertThat(ReportSnapshot.stats(flow.formatted(2, "ROSE"))).isEmpty();
		assertThat(ReportSnapshot.stats(flow.formatted(1, "SOARED"))).isEmpty();
		assertThat(ReportSnapshot.stats(flow.formatted(1, "ROSE").replace("\"previous\": 20", "\"previous\": \"20\"")))
			.isEmpty();
		assertThat(ReportSnapshot.stats(flow.formatted(1, "ROSE").replace("false", "\"no\""))).isEmpty();
		assertThat(ReportSnapshot.stats(null)).isEmpty();
	}

}
