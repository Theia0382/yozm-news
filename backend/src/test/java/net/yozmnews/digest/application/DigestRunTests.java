package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.digest.DigestRounds;
import net.yozmnews.digest.DigestStatus;
import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.digest.domain.digest.DigestPeriod;
import net.yozmnews.digest.domain.digest.DigestTrigger;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.user.DigestFailed;
import net.yozmnews.user.DigestFailureCause;
import net.yozmnews.user.UserTables;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * 생성 작업 유스케이스 — 실제 빈(쓸 모델 조회 · 후보 선별 · 생성)으로, 생성을 기다리는 다이제스트만 진행하고 쓸 모델이 없으면 생성기를 부르지 않고
 * 실패로 끝내며 실패 이벤트(갈래 키)를 내는지 본다. 생성기를 부르는 길은 구독이 없는 사용자의 후보 0 건(EMPTY)으로 본다 — 생성기는 부르지
 * 않는다. 기대값은 손으로 쓴 리터럴이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@RecordApplicationEvents
class DigestRunTests {

	@Autowired
	JobScheduler digestJobs;

	@Autowired
	DigestRun run;

	@Autowired
	DigestGeneration generation;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	ApplicationEvents events;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void stopDigestRounds() {
		DigestRounds.stop(this.digestJobs);
	}

	@AfterEach
	void clear() {
		awaitDigestFailuresHandled();
		this.jdbc.sql("delete from digests").update();
		this.jdbc.sql("delete from llm_credentials where user_id in (select id from users where email like 'digest-run-%')")
			.update();
		UserTables.deleteByEmail(this.jdbc, "digest-run-%");
	}

	@Test
	void aUserWithAModelAndNoSubscriptionsEndsEmptyThroughSelectionAndGeneration() {
		UserId user = user("digest-run-empty@example.com", "VALID");
		DigestId id = schedule(user);

		assertThat(this.run.run(id)).isEqualTo(DigestStatus.EMPTY);

		assertThat(row(id)).containsEntry("status", "EMPTY")
			.containsEntry("provider", "openai")
			.containsEntry("model", "gpt-6.1-terra")
			.containsEntry("candidate_count", 0);
		assertThat(this.events.stream(DigestFailed.class)).isEmpty();
	}

	@Test
	void aKeyInvalidatedAfterSchedulingEndsWithoutAModelAndAKeyFailure() {
		UserId user = user("digest-run-invalidated@example.com", "VALID");
		DigestId id = schedule(user);
		this.jdbc.sql("update llm_credentials set status = 'INVALID' where user_id = :user")
			.param("user", user.value())
			.update();

		assertThat(this.run.run(id)).isEqualTo(DigestStatus.FAILED);

		Map<String, Object> row = row(id);
		assertThat(row).containsEntry("status", "FAILED").containsEntry("error_code", "NO_USABLE_MODEL");
		assertThat(row.get("provider")).isNull();
		assertThat(row.get("input_tokens")).isNull();
		assertThat(row.get("duration_ms")).isNull();
		assertThat(this.events.stream(DigestFailed.class)).extracting(DigestFailed::user, DigestFailed::digestId,
				DigestFailed::digestDate, DigestFailed::cause)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(user, id.value(), LocalDate.parse("2026-11-19"),
					DigestFailureCause.KEY));
	}

	@Test
	void aSuspendedAccountEndsWithoutAModel() {
		UserId user = user("digest-run-suspended@example.com", "VALID");
		DigestId id = schedule(user);
		this.jdbc.sql("update users set status = 'SUSPENDED' where id = :id").param("id", user.value()).update();

		assertThat(this.run.run(id)).isEqualTo(DigestStatus.FAILED);

		assertThat(row(id)).containsEntry("error_code", "NO_USABLE_MODEL");
	}

	@Test
	void aDigestThatIsNotScheduledIsLeftAlone() {
		UserId user = user("digest-run-done@example.com", "VALID");
		DigestId id = schedule(user);
		assertThat(this.run.run(id)).isEqualTo(DigestStatus.EMPTY);

		// 끝난 다이제스트는 다시 진행하지 않는다 — 키가 없어져도 실패로 바꾸지 않는다
		this.jdbc.sql("delete from llm_credentials where user_id = :user").param("user", user.value()).update();
		assertThat(this.run.run(id)).isEqualTo(DigestStatus.EMPTY);

		DigestId running = schedule(user, "2026-11-20");
		this.jdbc.sql("update digests set status = 'RUNNING', started_at = now(), provider = 'openai', model = 'm' where id = :id")
			.param("id", running.value())
			.update();
		assertThat(this.run.run(running)).isEqualTo(DigestStatus.RUNNING);
		assertThat(row(running)).containsEntry("status", "RUNNING");
		assertThat(this.events.stream(DigestFailed.class)).isEmpty();
	}

	private DigestId schedule(UserId user) {
		return schedule(user, "2026-11-19");
	}

	private DigestId schedule(UserId user, String date) {
		return this.generation.schedule(user, LocalDate.parse(date),
				new DigestPeriod(Instant.parse("2026-11-17T22:00:00Z"), Instant.parse("2026-11-18T22:00:00Z")),
				DigestTrigger.MANUAL).id();
	}

	/** 쓰는 계정 — 설정 행(구독 없음) · OpenAI 키 하나(그 상태) */
	private UserId user(String email, String keyStatus) {
		UserId user = new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values (:email, '사용자', 'USER', 'ACTIVE', now(), 0) returning id
				""").param("email", email).query(Long.class).single());
		this.jdbc.sql("""
				insert into user_settings (user_id, tag_set_id, timezone, digest_time, weekly_report,
				    weekly_report_day, monthly_report, monthly_report_day, candidate_volume, big_slots, summary_length, reasoning_effort,
				    updated_at, version)
				values (:user, :tagSet, 'Asia/Seoul', '07:00', true, 1, true, 1, 'NORMAL', 0, 'NORMAL', 'MEDIUM', now(), 0)
				""").param("user", user.value()).param("tagSet", this.tagSets.activeTagSet().id().value()).update();
		this.jdbc.sql("""
				insert into llm_credentials (user_id, provider, ciphertext, encrypted_dek, kek_version, key_hint, status,
				  validated_at, created_at)
				values (:user, 'openai', '\\x01'::bytea, '\\x01'::bytea, 1, '', :status, now(), now())
				""").param("user", user.value()).param("status", keyStatus).update();
		return user;
	}

	private Map<String, Object> row(DigestId id) {
		return this.jdbc.sql("""
				select status, provider, model, candidate_count, input_tokens, duration_ms, error_code
				from digests where id = :id
				""").param("id", id.value()).query().singleRow();
	}

	/**
	 * 다이제스트 실패 이벤트를 알림 리스너가 처리해 발행을 끝냈다 — 사용자 행을 지우기 전에 기다린다. 리스너가 알림을 쓰기 전에 사용자를 지우면 외래 키
	 * 위반으로 발행이 미완료로 남아, 같은 DB 에서 발행이 비기를 기다리는 다른 시험이 재처리 주기까지 막힌다
	 */
	private void awaitDigestFailuresHandled() {
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(this.jdbc.sql("select count(*) from event_publication where event_type = :type")
				.param("type", DigestFailed.class.getName())
				.query(Long.class)
				.single()).isZero());
	}

}
