package net.yozmnews.report.domain.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import net.yozmnews.report.InterestShift;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportTrend;
import net.yozmnews.report.domain.input.Count;
import net.yozmnews.report.domain.input.InterestsSection;
import net.yozmnews.report.domain.input.StorylinesSection;
import net.yozmnews.report.domain.input.TopicInterestLine;
import net.yozmnews.report.domain.input.UserStorylineInterestLine;
import net.yozmnews.report.domain.input.UserStorylineMaterial;
import net.yozmnews.report.domain.material.ReportSnapshot.InterestStats;
import net.yozmnews.report.domain.material.ReportSnapshot.SnapshotStats;
import net.yozmnews.report.domain.material.ReportSnapshot.SnapshotUserStoryline;
import net.yozmnews.report.domain.material.ReportSnapshot.SnapshotValues;
import net.yozmnews.report.domain.material.ReportSnapshot.TopicLevelStat;
import net.yozmnews.report.domain.material.ReportSnapshot.UserStorylineLevelStat;
import net.yozmnews.report.domain.material.ReportSnapshot.UserStorylineStat;
import net.yozmnews.report.domain.material.ReportSnapshot.UserStorylineStats;
import net.yozmnews.report.domain.report.ReportPeriod;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineState;
import net.yozmnews.tag.TagSetId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 스냅숏 꼴 — 손으로 쓴 JSON 과 견준 쓰기와 읽기(다음 리포트가 견줄 값 · 모든 칸), 3판의 헤드 상태, 읽는 옛 판(2판 — 상태 없음 · 구독 주제
 * 보도량 칸은 읽지 않음), 모르는 판 · 깨진 문자열 · 꼴이 틀린 칸. 지키는 규칙: report-R19
 */
class ReportSnapshotTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final ReportPeriod WEEK = new ReportPeriod(ReportPeriodKind.WEEK,
			Instant.parse("2026-11-16T15:00:00Z"), Instant.parse("2026-11-23T15:00:00Z"));

	@Test
	void writesTheStatisticsOfTheSectionsInTheInputAsTheHandWrittenShape() {
		String written = ReportSnapshot.write(WEEK, new TagSetId(4), List.of(
				new StorylinesSection(List.of(new UserStorylineMaterial(new StorylineId(12),
						Set.of(new StorylineId(15), new StorylineId(12)), null, StorylineState.QUIET, false,
						new Count(30, 20L), List.of()))),
				new InterestsSection(
						List.of(new TopicInterestLine("economy.finance", "금융", 0.31, 0.52, InterestShift.ENTERED),
								new TopicInterestLine("economy.trade", "무역", 0.25, null, InterestShift.LEFT)),
						List.of(new UserStorylineInterestLine(new StorylineId(12), null, 0.4, 0.6, InterestShift.NONE)),
						List.of())));

		assertThat(JSON.readTree(written)).isEqualTo(JSON.readTree("""
				{ "version": 3, "tagSet": 4,
				  "period": { "kind": "WEEK", "start": "2026-11-16T15:00:00Z", "end": "2026-11-23T15:00:00Z" },
				  "storylines": { "basis": "PUBLISHED",
				    "userStorylines": [ { "head": 12, "tops": [12, 15], "articles": 30, "previous": 20, "trend": "ROSE",
				                 "trackedInPeriod": false, "state": "QUIET" } ] },
				  "interests": {
				    "topics": [ { "key": "economy.finance", "name": "금융", "start": 0.31, "end": 0.52, "shift": "ENTERED" },
				                { "key": "economy.trade", "name": "무역", "start": 0.25, "end": null, "shift": "LEFT" } ],
				    "userStorylines": [ { "head": 12, "start": 0.4, "end": 0.6, "shift": "NONE" } ] } }"""));
	}

	@Test
	void writesOnlyTheSectionsInTheInput() {
		assertThat(JSON.readTree(ReportSnapshot.write(WEEK, new TagSetId(4), List.of()))).isEqualTo(JSON.readTree("""
				{ "version": 3, "tagSet": 4,
				  "period": { "kind": "WEEK", "start": "2026-11-16T15:00:00Z", "end": "2026-11-23T15:00:00Z" } }"""));
	}

	@Test
	void readsTheValuesTheNextReportComparesFromAHandWrittenSnapshot() {
		assertThat(ReportSnapshot.read("""
				{ "version": 3, "tagSet": 3,
				  "period": { "kind": "WEEK", "start": "2026-11-09T15:00:00Z", "end": "2026-11-16T15:00:00Z" },
				  "storylines": { "basis": "PUBLISHED",
				    "userStorylines": [ { "head": 12, "tops": [15, 12], "articles": 20, "previous": null, "trend": "NO_COMPARISON",
				                 "trackedInPeriod": true, "state": "ACTIVE" } ] } }"""))
			.contains(new SnapshotValues(
					List.of(new SnapshotUserStoryline(new StorylineId(12), Set.of(new StorylineId(12), new StorylineId(15)), 20))));
		// 갈래가 없던 스냅숏은 값이 비었다
		assertThat(ReportSnapshot.read("""
				{ "version": 3, "tagSet": 3, "period": { "kind": "WEEK", "start": "2026-11-09T15:00:00Z",
				  "end": "2026-11-16T15:00:00Z" } }"""))
			.contains(new SnapshotValues(List.of()));
	}

	@Test
	void readsTheValuesOfThePreviousVersionWhoseStorylinesHaveNoStateAndWhoseTopicCoverageIsNotRead() {
		// 2판 — 흐름 줄에 상태가 없고 구독 주제 보도량 칸이 있다. 다음 주간이 견줄 흐름의 기사 수는 그대로 읽는다
		String previous = """
				{ "version": 2, "tagSet": 3,
				  "period": { "kind": "WEEK", "start": "2026-11-09T15:00:00Z", "end": "2026-11-16T15:00:00Z" },
				  "storylines": { "basis": "PUBLISHED",
				    "userStorylines": [ { "head": 12, "tops": [15, 12], "articles": 20, "previous": null, "trend": "NO_COMPARISON",
				                 "trackedInPeriod": true } ] },
				  "coverage": { "basis": "FIRST_EVALUATED",
				    "topics": [ { "key": "economy.finance", "name": "금융", "articles": 100, "previous": null,
				                  "trend": "NO_COMPARISON" } ] } }""";

		assertThat(ReportSnapshot.read(previous)).contains(new SnapshotValues(
				List.of(new SnapshotUserStoryline(new StorylineId(12), Set.of(new StorylineId(12), new StorylineId(15)), 20))));
		assertThat(ReportSnapshot.stats(previous)).contains(new SnapshotStats(new TagSetId(3),
				new UserStorylineStats(List.of(new UserStorylineStat(new StorylineId(12), Set.of(new StorylineId(12), new StorylineId(15)),
						20, null, ReportTrend.NO_COMPARISON, true, null))),
				null));
	}

	@Test
	void readsNothingFromAnUnknownVersionOrAnUnreadableText() {
		assertThat(ReportSnapshot.read("{\"version\": 4, \"tagSet\": 3}")).isEmpty();
		assertThat(ReportSnapshot.read("{\"version\": 1, \"tagSet\": 3}")).isEmpty();
		assertThat(ReportSnapshot.read("{\"tagSet\": 3}")).isEmpty();
		assertThat(ReportSnapshot.read("{\"storylines\":{\"이번\":12,\"직전\":7}}")).isEmpty();
		assertThat(ReportSnapshot.read("{\"version\": 3, \"tagSet\": 3, \"storylines\": {\"userStorylines\": [{\"head\": \"7\"}]}}"))
			.isEmpty();
		assertThat(ReportSnapshot.read("{")).isEmpty();
		assertThat(ReportSnapshot.read(null)).isEmpty();
	}

	@Test
	void statsReadsEveryFieldOfEachSectionFromAHandWrittenSnapshot() {
		assertThat(ReportSnapshot.stats("""
				{ "version": 3, "tagSet": 4,
				  "period": { "kind": "WEEK", "start": "2026-11-16T15:00:00Z", "end": "2026-11-23T15:00:00Z" },
				  "storylines": { "basis": "PUBLISHED",
				    "userStorylines": [ { "head": 12, "tops": [15, 12], "articles": 30, "previous": 20, "trend": "ROSE",
				                 "trackedInPeriod": false, "state": "ENDED" } ] },
				  "interests": {
				    "topics": [ { "key": "economy.finance", "name": "금융", "start": 0.25, "end": null, "shift": "LEFT" } ],
				    "userStorylines": [ { "head": 12, "start": null, "end": 0.6, "shift": "ENTERED" } ] } }"""))
			.contains(new SnapshotStats(new TagSetId(4),
					new UserStorylineStats(List.of(new UserStorylineStat(new StorylineId(12), Set.of(new StorylineId(12), new StorylineId(15)),
							30, 20L, ReportTrend.ROSE, false, StorylineState.ENDED))),
					new InterestStats(
							List.of(new TopicLevelStat("economy.finance", "금융", 0.25, null, InterestShift.LEFT)),
							List.of(new UserStorylineLevelStat(new StorylineId(12), null, 0.6, InterestShift.ENTERED)))));
	}

	@Test
	void statsLeavesASectionThatIsNotInTheSnapshotEmpty() {
		assertThat(ReportSnapshot.stats("""
				{ "version": 3, "tagSet": 4,
				  "period": { "kind": "MONTH", "start": "2026-10-31T15:00:00Z", "end": "2026-11-30T15:00:00Z" },
				  "interests": { "topics": [], "userStorylines": [] } }"""))
			.contains(new SnapshotStats(new TagSetId(4), null, new InterestStats(List.of(), List.of())));
	}

	@Test
	void statsReadsNothingFromAnUnknownVersionOrAFieldOfTheWrongKind() {
		String document = """
				{ "version": %d, "tagSet": 4, "storylines": { "userStorylines": [ { "head": 12, "tops": [12], "articles": 30,
				  "previous": 20, "trend": "%s", "trackedInPeriod": false, "state": "%s" } ] } }""";
		assertThat(ReportSnapshot.stats(document.formatted(3, "ROSE", "ACTIVE"))).isPresent();
		assertThat(ReportSnapshot.stats(document.formatted(4, "ROSE", "ACTIVE"))).isEmpty();
		assertThat(ReportSnapshot.stats(document.formatted(3, "SOARED", "ACTIVE"))).isEmpty();
		assertThat(ReportSnapshot.stats(document.formatted(3, "ROSE", "BUSY"))).isEmpty();
		assertThat(ReportSnapshot.stats(document.formatted(3, "ROSE", "ACTIVE").replace(", \"state\": \"ACTIVE\"", "")))
			.isEmpty();
		assertThat(ReportSnapshot.stats(document.formatted(3, "ROSE", "ACTIVE").replace("\"previous\": 20", "\"previous\": \"20\"")))
			.isEmpty();
		assertThat(ReportSnapshot.stats(document.formatted(3, "ROSE", "ACTIVE").replace("false", "\"no\""))).isEmpty();
		assertThat(ReportSnapshot.stats(null)).isEmpty();
	}

	@Test
	void statsReadsNothingFromTheFirstVersionWhoseUserStorylinesWereUnderTheOldKey() {
		// 1판은 추적한 사용자 스토리라인 · 관심 줄을 "flows" 로 썼다 — 저장된 1판은 마이그레이션이 2판으로 옮겼고, 읽기는 2판 · 3판만 읽는다
		assertThat(ReportSnapshot.stats("""
				{ "version": 1, "tagSet": 4, "storylines": { "basis": "PUBLISHED", "flows": [ { "head": 12, "tops": [12],
				  "articles": 30, "previous": 20, "trend": "ROSE", "trackedInPeriod": false } ] } }""")).isEmpty();
	}

}
