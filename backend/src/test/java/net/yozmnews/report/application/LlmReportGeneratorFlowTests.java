package net.yozmnews.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestClocks;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.domain.account.Account;
import net.yozmnews.account.domain.account.AccountRepository;
import net.yozmnews.account.domain.account.AccountRole;
import net.yozmnews.account.domain.account.DisplayName;
import net.yozmnews.account.domain.account.EmailAddress;
import net.yozmnews.credential.CredentialInvalidated;
import net.yozmnews.credential.domain.crypto.EnvelopeCipher;
import net.yozmnews.credential.domain.crypto.KeyOwner;
import net.yozmnews.credential.domain.key.KeyHint;
import net.yozmnews.credential.domain.key.LlmKeyRepository;
import net.yozmnews.credential.domain.key.StoredLlmKey;
import net.yozmnews.digest.DigestRounds;
import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.SecretValue;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportFixtures;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.adapter.out.llm.LlmReportGenerator;
import net.yozmnews.report.domain.content.ReportSectionRepository;
import net.yozmnews.report.domain.input.ReportInput;
import net.yozmnews.report.domain.report.ReportRepository;
import net.yozmnews.report.domain.report.ReportLedger;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.user.ReportFailed;
import net.yozmnews.user.ReportModelDirectory;
import net.yozmnews.user.UserTables;
import net.yozmnews.user.UserTimezones;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제 생성기의 전체 흐름 — profile {@code local} 문맥에서 관문의 로컬 대체물(가짜 키 {@code yozm-local-<낱말>})로 실제 생성기
 * ({@link LlmReportGenerator})를 지나 리포트가 끝나는 코드까지. {@code local} 의 결선된 생성기는 로컬 대체물이라 실제 생성기는 시험이 관문 빈으로
 * 직접 만들어 유스케이스의 패키지 생성자에 넣는다. 공급자에 닿지 않는다.
 * <p>
 * 키는 등록 처리기가 검증 성공 때만 저장하므로 저장소 · 봉투 암호로 넣고, 주인은 실제 계정이다 — 무효 이벤트를 알림 리스너가 받는다. 가짜 키
 * {@code valid} 의 본문은 출력 스키마의 필수 속성만 채운 가장 작은 JSON 이라 모든 갈래 칸이 있고 인용은 없다. {@code transient} 는 관문이
 * 2 · 4 · 8 초를 기다려 다시 보낸 뒤 실패한다(llm-R20). 잘림 · 거절 · 못 읽는 출력 · 빠진 갈래는 대역 생성기로 본다(ReportGenerationTests).
 * 생성 도중 키 무효는 생성 작업({@link ReportRun})부터 끝까지 지난다 — 쓸 모델을 정할 때는 유효하던 키를 공급자가 거절한다.
 * <p>
 * 지키는 규칙: report-R13 · report-R24 · llm-R20
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "spring.profiles.active=local,web,worker", "spring.docker.compose.enabled=false",
		"yozmnews.credential.kek-file=classpath:credential/test-kek", "jobrunr.background-job-server.enabled=false" })
class LlmReportGeneratorFlowTests {

	/** 입력 예산(128,000)은 본문 토큰과 다른 값이다 */
	private static final LlmModel MODEL = new LlmModel(Provider.OPENAI, "gpt-6.1-terra", 128_000, true);

	private static final Instant REGISTERED = Instant.parse("2026-11-14T06:00:00Z");

	@Autowired
	JobScheduler digestJobs;

	@Autowired
	LlmClientFactory gateway;

	@Autowired
	ReportRepository reports;

	@Autowired
	ReportSectionRepository sections;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	LlmKeyRepository keys;

	@Autowired
	EnvelopeCipher cipher;

	@Autowired
	AccountRepository accounts;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ReportEndings endings;

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

	@BeforeEach
	void stopDigestRounds() {
		DigestRounds.stop(this.digestJobs);
	}

	@AfterEach
	void clear() {
		awaitInvalidationsHandled();
		this.jdbc.sql("delete from reports").update();
		this.jdbc.sql("delete from llm_credentials").update();
		UserTables.deleteByEmail(this.jdbc, "report-flow-%");
	}

	@Test
	void theValidWordAnswersTheSmallestJsonWhichFillsEverySection() {
		Map<String, Object> row = runWithKey("valid");

		assertThat(row).containsEntry("status", "READY")
			.containsEntry("error_code", null)
			.containsEntry("input_tokens", 0)
			.containsEntry("output_tokens", 0);
		assertThat(this.jdbc.sql("select kind from report_sections where report_id = :id order by position")
			.param("id", row.get("id"))
			.query(String.class)
			.list()).containsExactly("OVERVIEW", "STORYLINES", "COVERAGE", "INTERESTS");
	}

	@Test
	void eachFakeKeyWordEndsTheReportWithItsCode() {
		assertThat(runWithKey("invalid")).containsEntry("status", "FAILED").containsEntry("error_code", "KEY_INVALID");
		assertThat(runWithKey("denied")).containsEntry("error_code", "PERMISSION_DENIED");
		assertThat(runWithKey("unexpected")).containsEntry("error_code", "PROVIDER_UNEXPECTED");
		// 관문 실패는 토큰이 없고 소요 시간은 있다
		Map<String, Object> quota = runWithKey("quota");
		assertThat(quota).containsEntry("error_code", "QUOTA_EXHAUSTED").containsEntry("input_tokens", null);
		assertThat(quota.get("duration_ms")).isNotNull();
	}

	@Test
	void aTransientFailureEndsAfterTheGatewaysResends() {
		Map<String, Object> row = runWithKey("transient");

		assertThat(row).containsEntry("error_code", "PROVIDER_UNAVAILABLE");
		// 관문이 2 · 4 · 8 초를 기다렸다 — 소요 시간은 그 합 이상
		assertThat((Long) row.get("duration_ms")).isGreaterThanOrEqualTo(14_000L);
	}

	@Test
	void aUserWithoutAKeyEndsAsKeyMissing() {
		UserId user = account("report-flow-nokey@example.com");

		Map<String, Object> row = run(user);

		assertThat(row).containsEntry("error_code", "KEY_MISSING")
			.containsEntry("error_message", "쓸 모델 공급자의 저장한 키가 없다")
			.containsEntry("input_tokens", null);
	}

	@Test
	void aKeyFoundInvalidDuringTheRunEndsTheScheduledReportAndNotifiesTheUser() {
		// 저장한 키는 유효 상태라 쓸 모델이 있다 — 공급자가 생성 도중 키를 거절한다
		UserId user = account("report-flow-run-invalid@example.com");
		store(user, "yozm-local-invalid-0123456789");
		this.jdbc.sql("""
				insert into user_settings (user_id, tag_set_id, timezone, digest_time, weekly_report,
				    weekly_report_day, monthly_report, monthly_report_day, candidate_volume, big_slots, summary_length,
				    reasoning_effort, updated_at, version)
				values (:user, :tagSet, 'Asia/Seoul', '07:00', true, 1, true, 1, 'NORMAL', 3, 'NORMAL', 'MEDIUM', now(), 0)
				""").param("user", user.value()).param("tagSet", this.tagSets.activeTagSet().id().value()).update();
		// 구독 주제 하나 — 보도량 갈래가 있어 생성기를 부른다
		this.jdbc.sql("""
				insert into user_tag_preferences (user_id, tag_kind, tag_key, mode, created_at)
				values (:user, 'TOPIC', 'economy.finance', 'SUBSCRIBE', now())
				""").param("user", user.value()).update();
		ReportGeneration generation = new ReportGeneration(this.reports, this.sections, this.endings,
				new LlmReportGenerator(this.gateway), this.transaction, TestClocks.of(Instant::now));
		ReportId id = generation.schedule(user, ReportFixtures.PERIOD).id();

		ReportStatus status = new ReportRun(this.reports, this.ledger, this.models, this.timezones, this.collection,
				generation, this.endings, this.transaction, Instant::now)
			.run(id);

		assertThat(status).isEqualTo(ReportStatus.FAILED);
		assertThat(this.jdbc.sql("""
				select status, error_code, started_at is not null as started from reports where id = :id
				""").param("id", id.value()).query().singleRow()).containsEntry("status", "FAILED")
			.containsEntry("error_code", "KEY_INVALID")
			.containsEntry("started", true);
		// 키는 무효가 되고, 사용자는 키 무효 알림과 리포트 실패 알림을 하나씩 받는다
		assertThat(this.jdbc.sql("select status from llm_credentials where user_id = :user")
			.param("user", user.value())
			.query(String.class)
			.single()).isEqualTo("INVALID");
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(this.jdbc.sql("select kind from user_notices where user_id = :user order by kind")
				.param("user", user.value())
				.query(String.class)
				.list()).containsExactly("KEY_INVALIDATED", "REPORT_FAILED"));
	}

	/** 그 낱말의 가짜 키를 가진 새 사용자로 생성한다 */
	private Map<String, Object> runWithKey(String word) {
		UserId user = account("report-flow-" + word + "-" + System.nanoTime() + "@example.com");
		store(user, "yozm-local-" + word + "-0123456789");
		return run(user);
	}

	private Map<String, Object> run(UserId user) {
		ReportGeneration generation = new ReportGeneration(this.reports, this.sections, this.endings,
				new LlmReportGenerator(this.gateway), this.transaction, TestClocks.of(Instant::now));
		ReportId id = generation.schedule(user, ReportFixtures.PERIOD).id();
		ReportInput input = ReportFixtures.input(user, ReportFixtures.PERIOD,
				ReportFixtures.userStorylines(ReportFixtures.userStoryline(31, 902)), ReportFixtures.coverage(), ReportFixtures.interests(31));
		ReportStatus status = generation.run(id, MODEL, ReportFixtures.EFFORT, input);
		assertThat(status).isIn(ReportStatus.READY, ReportStatus.FAILED);
		return this.jdbc.sql("""
				select id, status, input_tokens, output_tokens, duration_ms, error_code, error_message
				from reports where id = :id
				""").param("id", id.value()).query().singleRow();
	}

	private UserId account(String email) {
		return this.accounts.save(Account.create(EmailAddress.parse(email).orElseThrow(), new DisplayName("흐름"),
				AccountRole.USER, REGISTERED))
			.id();
	}

	private void store(UserId user, String key) {
		KeyOwner owner = new KeyOwner(user, MODEL.provider());
		SecretValue value = SecretValue.of(key);
		this.keys.save(StoredLlmKey.register(owner, this.cipher.seal(value, owner), KeyHint.of(value), REGISTERED));
	}

	/**
	 * 키 무효 이벤트(invalid · denied)를 알림 리스너가 처리해 발행을 끝냈다 — 사용자 행을 지우기 전에 기다린다. 리스너가 알림을 쓰기 전에 사용자를
	 * 지우면 발행이 미완료로 남아, 같은 DB 에서 발행이 비기를 기다리는 다른 시험이 재처리 주기까지 막힌다
	 */
	private void awaitInvalidationsHandled() {
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(this.jdbc.sql("select count(*) from event_publication where event_type in (:types)")
				.param("types", List.of(CredentialInvalidated.class.getName(), ReportFailed.class.getName()))
				.query(Long.class)
				.single()).isZero());
	}

}
