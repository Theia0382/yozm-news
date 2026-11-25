package net.yozmnews.report.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import javax.sql.DataSource;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.report.InterestShift;
import net.yozmnews.report.ReportTrend;
import net.yozmnews.report.domain.material.ReportSnapshot;
import net.yozmnews.report.domain.material.ReportSnapshot.SnapshotStats;
import net.yozmnews.report.domain.material.ReportSnapshot.UserStorylineLevelStat;
import net.yozmnews.report.domain.material.ReportSnapshot.UserStorylineStat;
import net.yozmnews.storyline.StorylineId;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 리포트 스냅숏을 1판에서 2판으로 옮기는 마이그레이션(V87) — 실제 PostgreSQL 의 별도 스키마에 V86 까지만 올려 1판 스냅숏을 가진 리포트
 * 행(갈래 셋 · 추적 갈래 없음), 1판이 아닌 행, 스냅숏이 없는 행을 넣고, 끝까지 올린 뒤 1판만 2판 · 새 키 · 같은 값이 되는지와 읽기가 넣은 숫자를 내는지 본다. 앱이
 * 쓰는 스키마와 섞이지 않게 스키마를 따로 두고 테스트 뒤 지운다.
 * <p>
 * 지키는 규칙: report-R19
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ReportSnapshotMigrationTests {

	private static final String SCHEMA = "v86_report_snapshot";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String ALL_SECTIONS = """
			{"version": 1, "tagSet": 4,
			 "period": {"kind": "WEEK", "start": "2026-11-16T15:00:00Z", "end": "2026-11-23T15:00:00Z"},
			 "storylines": {"basis": "PUBLISHED", "flows": [
			   {"head": 12, "tops": [12, 15], "articles": 30, "previous": 20, "trend": "ROSE", "trackedInPeriod": false},
			   {"head": 40, "tops": [40], "articles": 7, "previous": null, "trend": "NO_COMPARISON", "trackedInPeriod": true}]},
			 "coverage": {"basis": "FIRST_EVALUATED", "topics": [
			   {"key": "economy.finance", "name": "금융", "articles": 120, "previous": 100, "trend": "STEADY"}]},
			 "interests": {"topics": [{"key": "economy.finance", "name": "금융", "start": 0.31, "end": 0.52, "shift": "ENTERED"}],
			   "flows": [{"head": 12, "start": 0.4, "end": 0.6, "shift": "NONE"}]}}""";

	private static final String COVERAGE_ONLY = """
			{"version": 1, "tagSet": 4,
			 "period": {"kind": "WEEK", "start": "2026-11-16T15:00:00Z", "end": "2026-11-23T15:00:00Z"},
			 "coverage": {"basis": "FIRST_EVALUATED", "topics": []}}""";

	/** 1판이 아닌 스냅숏 — 옛 키가 있어도 옮기지 않는다 */
	private static final String OTHER_VERSION = """
			{"version": 3, "tagSet": 4,
			 "period": {"kind": "WEEK", "start": "2026-11-16T15:00:00Z", "end": "2026-11-23T15:00:00Z"},
			 "storylines": {"basis": "PUBLISHED", "flows": [
			   {"head": 12, "tops": [12], "articles": 30, "previous": 20, "trend": "ROSE", "trackedInPeriod": false}]}}""";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void firstVersionSnapshotsMoveToTheSecondVersionKeepingTheirValues() {
		flyway("86").migrate();
		long all = ready(ALL_SECTIONS);
		long coverageOnly = ready(COVERAGE_ONLY);
		long otherVersion = ready(OTHER_VERSION);
		long scheduled = this.jdbc.sql("insert into " + SCHEMA + ".reports (user_id, period_kind, period_start, period_end, "
				+ "status, created_at, version) values (7, 'WEEK', '2026-11-23T15:00:00Z', '2026-11-30T15:00:00Z', 'SCHEDULED', "
				+ "now(), 0) returning id")
			.query(Long.class)
			.single();

		flyway(null).migrate();

		assertThat(JSON.readTree(snapshot(all))).isEqualTo(JSON.readTree("""
				{"version": 2, "tagSet": 4,
				 "period": {"kind": "WEEK", "start": "2026-11-16T15:00:00Z", "end": "2026-11-23T15:00:00Z"},
				 "storylines": {"basis": "PUBLISHED", "userStorylines": [
				   {"head": 12, "tops": [12, 15], "articles": 30, "previous": 20, "trend": "ROSE", "trackedInPeriod": false},
				   {"head": 40, "tops": [40], "articles": 7, "previous": null, "trend": "NO_COMPARISON", "trackedInPeriod": true}]},
				 "coverage": {"basis": "FIRST_EVALUATED", "topics": [
				   {"key": "economy.finance", "name": "금융", "articles": 120, "previous": 100, "trend": "STEADY"}]},
				 "interests": {"topics": [{"key": "economy.finance", "name": "금융", "start": 0.31, "end": 0.52, "shift": "ENTERED"}],
				   "userStorylines": [{"head": 12, "start": 0.4, "end": 0.6, "shift": "NONE"}]}}"""));
		// 옮긴 스냅숏을 읽기가 넣은 숫자 그대로 낸다
		SnapshotStats stats = ReportSnapshot.stats(snapshot(all)).orElseThrow();
		assertThat(stats.userStorylines().userStorylines()).containsExactly(
				new UserStorylineStat(new StorylineId(12), Set.of(new StorylineId(12), new StorylineId(15)), 30, 20L,
						ReportTrend.ROSE, false),
				new UserStorylineStat(new StorylineId(40), Set.of(new StorylineId(40)), 7, null, ReportTrend.NO_COMPARISON,
						true));
		assertThat(stats.interests().userStorylines())
			.containsExactly(new UserStorylineLevelStat(new StorylineId(12), 0.4, 0.6, InterestShift.NONE));
		// 추적 갈래가 없던 스냅숏은 판만 오른다
		assertThat(JSON.readTree(snapshot(coverageOnly))).isEqualTo(JSON.readTree("""
				{"version": 2, "tagSet": 4,
				 "period": {"kind": "WEEK", "start": "2026-11-16T15:00:00Z", "end": "2026-11-23T15:00:00Z"},
				 "coverage": {"basis": "FIRST_EVALUATED", "topics": []}}"""));
		// 대조 — 1판이 아닌 스냅숏은 판도 키도 그대로다
		assertThat(JSON.readTree(snapshot(otherVersion))).isEqualTo(JSON.readTree(OTHER_VERSION));
		// 시작 전 행에는 스냅숏이 없다
		assertThat(snapshot(scheduled)).isNull();
	}

	private long ready(String snapshot) {
		return this.jdbc.sql("insert into " + SCHEMA + ".reports (user_id, period_kind, period_start, period_end, status, "
				+ "provider, model, input_tokens, output_tokens, duration_ms, stats_snapshot, created_at, started_at, "
				+ "completed_at, version, zone) values (7, 'WEEK', '2026-11-16T15:00:00Z', '2026-11-23T15:00:00Z', 'READY', "
				+ "'openai', 'gpt', 10, 20, 30, cast(:snapshot as jsonb), now(), now(), now(), 0, 'Asia/Seoul') returning id")
			.param("snapshot", snapshot)
			.query(Long.class)
			.single();
	}

	private @Nullable String snapshot(long id) {
		return this.jdbc.sql("select stats_snapshot::text from " + SCHEMA + ".reports where id = :id")
			.param("id", id)
			.query(String.class)
			.list()
			.getFirst();
	}

	private Flyway flyway(@Nullable String target) {
		var configuration = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true);
		return ((target != null) ? configuration.target(target) : configuration).load();
	}

}
