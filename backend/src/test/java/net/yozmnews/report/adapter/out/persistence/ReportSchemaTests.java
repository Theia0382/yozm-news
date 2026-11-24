package net.yozmnews.report.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.yozmnews.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 리포트 테이블의 제약 — 섹션 종류는 개요와 갈래 셋, 인용은 스토리라인 · 사건 가운데 하나, 시작한 행은 스냅숏 · 시간대가 있다, 직전 리포트는
 * 다른 행이고 지워지면 칸이 빈다.
 * <p>
 * 지키는 규칙: report-R1 · report-R9 · report-R14 · report-R18 · report-R26
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ReportSchemaTests {

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void clear() {
		this.jdbc.sql("delete from reports").update();
	}

	@Test
	void anItemPointsToExactlyOneOfAStorylineAndAnEvent() {
		long section = section();

		assertThatThrownBy(() -> item(section, 0, 31L, 902L)).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("report_items_one_target");
		assertThatThrownBy(() -> item(section, 0, null, null)).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("report_items_one_target");
		item(section, 0, 31L, null);
		item(section, 1, null, 902L);
		assertThat(this.jdbc.sql("select count(*) from report_items where report_section_id = :s")
			.param("s", section)
			.query(Long.class)
			.single()).isEqualTo(2L);
	}

	@Test
	void aSectionKindIsTheOverviewOrOneOfTheThreeMaterialKinds() {
		long report = scheduled();

		// 대조 — 개요와 갈래 셋은 넣어진다
		section(report, "OVERVIEW", 0);
		section(report, "STORYLINES", 1);
		section(report, "COVERAGE", 2);
		section(report, "INTERESTS", 3);
		assertThat(this.jdbc.sql("select count(*) from report_sections where report_id = :r")
			.param("r", report)
			.query(Long.class)
			.single()).isEqualTo(4L);
		assertThatThrownBy(() -> section(report, "RISE_AND_FALL", 4)).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("report_sections_kind_check");
	}

	@Test
	void aStartedRowHasASnapshot() {
		long id = scheduled();

		assertThatThrownBy(() -> this.jdbc.sql("""
				update reports set status = 'RUNNING', provider = 'openai', model = 'gpt-5.5-mini', started_at = now()
				where id = :id""").param("id", id).update()).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("reports_snapshot");
		this.jdbc.sql("update reports set stats_snapshot = cast('{}' as jsonb), zone = 'Asia/Seoul' where id = :id")
			.param("id", id)
			.update();
		this.jdbc.sql("""
				update reports set status = 'RUNNING', provider = 'openai', model = 'gpt-5.5-mini', started_at = now()
				where id = :id""").param("id", id).update();
		assertThat(this.jdbc.sql("select status from reports where id = :id").param("id", id).query(String.class).single())
			.isEqualTo("RUNNING");
	}

	@Test
	void aStartedRowHasAZoneWhileAScheduledRowNeedsNone() {
		long id = scheduled();
		this.jdbc.sql("update reports set stats_snapshot = cast('{}' as jsonb) where id = :id").param("id", id).update();

		assertThatThrownBy(() -> this.jdbc.sql("""
				update reports set status = 'RUNNING', provider = 'openai', model = 'gpt-5.5-mini', started_at = now()
				where id = :id""").param("id", id).update()).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("reports_zone");
		assertThat(this.jdbc.sql("select status from reports where id = :id").param("id", id).query(String.class).single())
			.isEqualTo("SCHEDULED");
	}

	@Test
	void aPreviousReportIsAnotherRowAndLeavingItEmptiesTheColumn() {
		long previous = scheduled();
		long current = scheduled();

		assertThatThrownBy(() -> this.jdbc.sql("update reports set previous_report_id = id where id = :id")
			.param("id", current)
			.update()).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("reports_previous_not_self");
		assertThatThrownBy(() -> this.jdbc.sql("update reports set previous_report_id = :missing where id = :id")
			.param("missing", previous + 1_000)
			.param("id", current)
			.update()).isInstanceOf(DataIntegrityViolationException.class);
		this.jdbc.sql("update reports set previous_report_id = :previous where id = :id")
			.param("previous", previous)
			.param("id", current)
			.update();
		this.jdbc.sql("delete from reports where id = :id").param("id", previous).update();
		assertThat(this.jdbc.sql("select previous_report_id is null from reports where id = :id")
			.param("id", current)
			.query(Boolean.class)
			.single()).isTrue();
	}

	private long scheduled() {
		return this.jdbc.sql("""
				insert into reports (user_id, period_kind, period_start, period_end, status, created_at, version)
				values (7, 'WEEK', timestamptz '2026-11-16 00:00Z', timestamptz '2026-11-23 00:00Z', 'SCHEDULED', now(), 0)
				returning id""").query(Long.class).single();
	}

	private long section() {
		long report = scheduled();
		return this.jdbc.sql("""
				insert into report_sections (report_id, kind, position, title, body)
				values (:r, 'COVERAGE', 1, '보도량', '본문') returning id""").param("r", report).query(Long.class).single();
	}

	private void section(long report, String kind, int position) {
		this.jdbc.sql("""
				insert into report_sections (report_id, kind, position, title, body)
				values (:r, :kind, :p, '제목', '본문')""").param("r", report).param("kind", kind).param("p", position).update();
	}

	private void item(long section, int position, Long storyline, Long event) {
		this.jdbc.sql("""
				insert into report_items (report_section_id, position, storyline_id, event_id)
				values (:s, :p, :storyline, :event)""")
			.param("s", section)
			.param("p", position)
			.param("storyline", storyline, java.sql.Types.BIGINT)
			.param("event", event, java.sql.Types.BIGINT)
			.update();
	}

}
