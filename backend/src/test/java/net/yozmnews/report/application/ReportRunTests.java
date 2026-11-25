package net.yozmnews.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.Map;

import net.yozmnews.TestClocks;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportFixtures;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.adapter.out.fake.FakeReportGenerator;
import net.yozmnews.report.domain.content.ReportSectionRepository;
import net.yozmnews.report.domain.report.ReportLedger;
import net.yozmnews.report.domain.report.ReportRepository;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.user.ReportModelDirectory;
import net.yozmnews.user.UserTables;
import net.yozmnews.user.UserTimezones;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 생성 작업 — 생성을 기다리는 리포트만 진행하고, 쓸 모델이 없으면 생성기를 부르지 않고 시작하지 않은 채 쓸 모델 없음으로 끝내며 그때의 사용자
 * 시간대를 적는다. 쓸 모델이 있으면 재료를 모아 생성한다. 실제 PostgreSQL, 생성기는 로컬 대체물.
 * <p>
 * 지키는 규칙: report-R1 · report-R13
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ReportRunTests {

	private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");

	@Autowired
	ReportRepository reports;

	@Autowired
	ReportSectionRepository sections;

	@Autowired
	ReportLedger ledger;

	@Autowired
	ReportModelDirectory models;

	@Autowired
	UserTimezones timezones;

	@Autowired
	ReportMaterialCollection collection;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from reports").update();
		this.jdbc.sql("delete from llm_credentials where user_id in (select id from users where email like 'report-run-%')")
			.update();
		UserTables.deleteByEmail(this.jdbc, "report-run-%");
	}

	private ReportRun run() {
		ReportEndings endings = new ReportEndings(this.reports);
		ReportGeneration generation = new ReportGeneration(this.reports, this.sections, endings,
				new FakeReportGenerator(), this.transaction, TestClocks.of(() -> NOW));
		return new ReportRun(this.reports, this.ledger, this.models, this.timezones, this.collection, generation,
				endings, this.transaction, () -> NOW);
	}

	@Test
	void withoutAUsableModelItEndsWithoutStartingAndWritesTheUsersZone() {
		UserId user = user("report-run-keyless@example.com", false);
		ReportId id = schedule(user);

		assertThat(run().run(id)).isEqualTo(ReportStatus.FAILED);

		assertThat(row(id)).containsEntry("status", "FAILED")
			.containsEntry("error_code", "NO_USABLE_MODEL")
			.containsEntry("zone", "America/Denver")
			.containsEntry("started", false)
			.containsEntry("provider", null);
	}

	@Test
	void withAUsableModelItCollectsAndGeneratesTheScheduledReport() {
		UserId user = user("report-run-keyed@example.com", true);
		ReportId id = schedule(user);

		ReportStatus status = run().run(id);

		// 구독 · 추적 · 관심이 없는 새 사용자 — 갈래가 없어 생성기를 부르지 않고 재료 없음으로 끝난다
		assertThat(status).isEqualTo(ReportStatus.EMPTY);
		assertThat(row(id)).containsEntry("status", "EMPTY")
			.containsEntry("zone", "America/Denver")
			.containsEntry("started", true)
			.containsEntry("provider", "openai");
	}

	@Test
	void aReportThatIsNotScheduledIsLeftAsItIs() {
		UserId user = user("report-run-twice@example.com", false);
		ReportId id = schedule(user);
		run().run(id);

		// 끝난 리포트를 다시 부르면 그 상태를 돌려주고 바꾸지 않는다
		assertThat(run().run(id)).isEqualTo(ReportStatus.FAILED);
		assertThat(row(id)).containsEntry("error_code", "NO_USABLE_MODEL");
		assertThatIllegalArgumentException().isThrownBy(() -> run().run(new ReportId(Long.MAX_VALUE)));
	}

	private ReportId schedule(UserId user) {
		return new ReportGeneration(this.reports, this.sections, new ReportEndings(this.reports),
				new FakeReportGenerator(), this.transaction, TestClocks.of(() -> NOW))
			.schedule(user, ReportFixtures.PERIOD)
			.id();
	}

	/** 쓰는 계정 — `America/Denver`, 키가 있으면 OpenAI 유효 키 */
	private UserId user(String email, boolean withKey) {
		UserId user = new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values (:email, '사용자', 'USER', 'ACTIVE', now(), 0) returning id
				""").param("email", email).query(Long.class).single());
		this.jdbc.sql("""
				insert into user_settings (user_id, tag_set_id, timezone, digest_time, weekly_report,
				    weekly_report_day, monthly_report, monthly_report_day, candidate_volume, big_slots, summary_length,
				    reasoning_effort, updated_at, version)
				values (:user, :tagSet, 'America/Denver', '03:17', true, 4, true, 19, 'NORMAL', 3, 'NORMAL', 'MEDIUM',
				    now(), 0)
				""").param("user", user.value()).param("tagSet", this.tagSets.activeTagSet().id().value()).update();
		if (withKey) {
			this.jdbc.sql("""
					insert into llm_credentials (user_id, provider, ciphertext, encrypted_dek, kek_version, key_hint, status,
					  validated_at, created_at)
					values (:user, 'openai', '\\x01'::bytea, '\\x01'::bytea, 1, '', 'VALID', now(), now())
					""").param("user", user.value()).update();
		}
		return user;
	}

	private Map<String, Object> row(ReportId id) {
		return this.jdbc.sql("""
				select status, error_code, zone, started_at is not null as started, provider from reports where id = :id
				""").param("id", id.value()).query().singleRow();
	}

}
