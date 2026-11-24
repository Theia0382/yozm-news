package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.digest.ReadyDigestDays;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * READY 다이제스트의 날짜 수 — 기간 끝이 [from, to) 인 READY 다이제스트의 서로 다른 날짜를 센다. 실제 PostgreSQL 위에서 다이제스트 행을 직접
 * 넣는다. 기대값은 손으로 센 리터럴이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ReadyDigestDaysServiceTests {

	private static final UserId USER = new UserId(240_001);

	private static final UserId OTHER = new UserId(240_002);

	/** 주간 기간 [월 07:00, 다음 월 07:00) KST — UTC 로 일요일 22:00 */
	private static final Instant FROM = Instant.parse("2026-11-15T22:00:00Z");

	private static final Instant TO = Instant.parse("2026-11-22T22:00:00Z");

	@Autowired
	ReadyDigestDays days;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from digests where user_id in (:users)")
			.param("users", List.of(USER.value(), OTHER.value()))
			.update();
	}

	@Test
	void countsDistinctDatesNotRows() {
		// 같은 날짜(11-17)의 READY 둘 — 수동으로 다시 만든 날. 행은 셋, 날짜는 둘
		digest(USER, "2026-11-16", "2026-11-15T22:00:00Z", "READY");
		digest(USER, "2026-11-17", "2026-11-16T22:00:00Z", "READY");
		digest(USER, "2026-11-17", "2026-11-17T03:00:00Z", "READY");

		assertThat(this.days.count(USER, FROM, TO)).isEqualTo(2);
	}

	@Test
	void onlyReadyDigestsWhosePeriodEndIsInsideTheRangeCount() {
		digest(USER, "2026-11-16", "2026-11-15T22:00:00Z", "READY"); // 시작과 같은 끝 — 든다
		digest(USER, "2026-11-18", "2026-11-17T22:00:00Z", "EMPTY");
		digest(USER, "2026-11-19", "2026-11-18T22:00:00Z", "FAILED");
		digest(USER, "2026-11-20", "2026-11-19T22:00:00Z", "SCHEDULED");
		digest(USER, "2026-11-23", "2026-11-22T22:00:00Z", "READY"); // 끝과 같은 끝 — 다음 주에 든다
		digest(USER, "2026-11-15", "2026-11-14T22:00:00Z", "READY"); // 앞 주
		digest(OTHER, "2026-11-17", "2026-11-16T22:00:00Z", "READY"); // 다른 사용자

		assertThat(this.days.count(USER, FROM, TO)).isEqualTo(1);
		// 대조 — 같은 기간에서 다른 사용자는 자기 것만 센다
		assertThat(this.days.count(OTHER, FROM, TO)).isEqualTo(1);
	}

	@Test
	void refusesARangeWhoseEndIsNotAfterItsStart() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.days.count(USER, TO, FROM));
		assertThatIllegalArgumentException().isThrownBy(() -> this.days.count(USER, FROM, FROM));
	}

	private void digest(UserId user, String date, String periodEnd, String status) {
		boolean started = !status.equals("SCHEDULED");
		boolean finished = status.equals("READY") || status.equals("EMPTY") || status.equals("FAILED");
		this.jdbc.sql("""
				insert into digests (user_id, digest_date, period_start, period_end, status, trigger, error_code,
				    created_at, dispatch_token, dispatched_at, started_at, completed_at, version)
				values (:user, cast(:date as date), cast(:end as timestamptz) - interval '1 day', cast(:end as timestamptz),
				    :status, 'MANUAL', :failure, cast(:end as timestamptz), gen_random_uuid(), cast(:end as timestamptz),
				    :started, :completed, 0)
				""")
			.param("user", user.value())
			.param("date", date)
			.param("end", periodEnd)
			.param("status", status)
			.param("failure", status.equals("FAILED") ? "PROVIDER_UNAVAILABLE" : null)
			.param("started", started ? Timestamp.from(Instant.parse(periodEnd)) : null)
			.param("completed", finished ? Timestamp.from(Instant.parse(periodEnd)) : null)
			.update();
	}

}
