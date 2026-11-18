package net.yozmnews.digest.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.digest.DigestRounds;
import net.yozmnews.digest.domain.view.DigestDayRepository;
import net.yozmnews.digest.domain.view.FailedAttempt;
import net.yozmnews.platform.UserId;
import org.jobrunr.scheduling.JobScheduler;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 해결되지 않은 마지막 실패의 재료 — 그 사용자의 가장 최근 실패(ID 가 가장 큰 FAILED 행)와 기간을 덮는 행(READY · EMPTY) 가운데 가장 큰 ID.
 * 생성을 기다리거나 생성 중인 행 · 실패 행은 덮지 않는다. 다른 사용자의 행은 보지 않는다.
 * <p>
 * 실제 PostgreSQL 위에서 다이제스트 행을 직접 넣는다(사용자 테이블과 FK 가 없다). 지키는 규칙: digest-R30 · digest-R40
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class JdbcDigestDayRepositoryTests {

	private static final long USER = 52;

	private static final long OTHER = 53;

	@Autowired
	JobScheduler digestJobs;

	@Autowired
	DigestDayRepository days;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void stopDigestRounds() {
		DigestRounds.stop(this.digestJobs);
	}

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from digests").update();
	}

	@Test
	void theLatestFailureAndTheLatestCoveringRowAreTheUsersOwn() {
		row(USER, "2026-11-15", "FAILED", "2026-11-14T22:03:00Z");
		long ready = row(USER, "2026-11-16", "READY", "2026-11-15T22:03:00Z");
		row(USER, "2026-11-17", "RUNNING", null);
		long failed = row(USER, "2026-11-17", "FAILED", "2026-11-16T22:43:00Z");
		row(OTHER, "2026-11-18", "READY", "2026-11-17T22:03:00Z");
		row(OTHER, "2026-11-18", "FAILED", "2026-11-17T23:03:00Z");

		assertThat(this.days.latestFailed(new UserId(USER)))
			.contains(new FailedAttempt(failed, LocalDate.parse("2026-11-17"), Instant.parse("2026-11-16T22:43:00Z")));
		// RUNNING · FAILED 는 기간을 덮지 않는다
		assertThat(this.days.latestCoveringId(new UserId(USER))).hasValue(ready);
	}

	@Test
	void anEmptyRowCoversToo() {
		row(USER, "2026-11-16", "READY", "2026-11-15T22:03:00Z");
		long empty = row(USER, "2026-11-17", "EMPTY", "2026-11-16T22:03:00Z");

		assertThat(this.days.latestCoveringId(new UserId(USER))).hasValue(empty);
		assertThat(this.days.latestFailed(new UserId(USER))).isEmpty();
	}

	@Test
	void withoutRowsBothAreEmpty() {
		assertThat(this.days.latestFailed(new UserId(USER))).isEmpty();
		assertThat(this.days.latestCoveringId(new UserId(USER))).isEmpty();
	}

	/** 다이제스트 행 하나 — 수동 계기(같은 날짜에 여럿), 기간은 그 날짜 전날 하루. 끝난 상태면 그 시각에 끝냈고, 실패면 코드는 공급자 무응답이다 */
	private long row(long user, String date, String status, @Nullable String completedAt) {
		boolean started = !status.equals("SCHEDULED");
		return this.jdbc.sql("""
				insert into digests (user_id, digest_date, period_start, period_end, status, trigger, provider, model,
				    error_code, created_at, dispatch_token, dispatched_at, started_at, completed_at, version)
				values (:user, cast(:date as date), cast(:date as timestamptz) - interval '1 day', cast(:date as timestamptz),
				    :status, 'MANUAL', :provider, :model, :failure, cast(:date as timestamptz), gen_random_uuid(),
				    cast(:date as timestamptz), :started, :completed, 0)
				returning id
				""")
			.param("user", user)
			.param("date", date)
			.param("status", status)
			.param("provider", started ? "openai" : null)
			.param("model", started ? "m" : null)
			.param("failure", status.equals("FAILED") ? "PROVIDER_UNAVAILABLE" : null)
			.param("started", started ? Timestamp.from(Instant.parse(date + "T00:00:00Z")) : null)
			.param("completed", (completedAt == null) ? null : Timestamp.from(Instant.parse(completedAt)))
			.query(Long.class)
			.single();
	}

}
