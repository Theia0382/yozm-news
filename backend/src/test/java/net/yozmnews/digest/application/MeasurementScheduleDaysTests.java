package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.MeasurementClockRow;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.ClockAdvanceResult;
import net.yozmnews.platform.MeasurementClockAdministration;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 측정용 시계로 하루씩 넘기는 일정 생성 — 측정 profile 로 뜬 앱의 실제 결선(측정용 시계 · 일정 회차 · 일정 목록 · 쓸 모델 조회)에서, 시계를 하루씩
 * 넘길 때마다 일정 회차가 그 날짜의 다이제스트를 한 번 만들고 기간이 직전 다이제스트의 끝부터 이어지는지 본다. 생성은 돌리지 않고 만든 행을 READY 로
 * 끝낸다. 작업 서버는 끈다(등록한 생성 작업이 돌지 않는다).
 * <p>
 * 시험 사용자의 생성 시각은 `America/Denver` 03:17(= 10:17 UTC)이다. 첫날은 2026-11-19 10:18 UTC 로 넘기고 그 뒤로 24시간씩 넘긴다. 생성 시각이
 * 21:00 인 대조 사용자는 따라잡기 창(생성 시각 뒤 2시간)이 넘긴 구간에 들어가 날짜를 받지 못한다. 기대값은 손으로 셈한 리터럴이다.
 * MeasurementClockStartupTests 와 같은 구성이라 컨텍스트를 함께 쓴다.
 */
@Import({ TestcontainersConfiguration.class, MeasurementClockRow.class })
@SpringBootTest
@ActiveProfiles({ "web", "worker", "measurement" })
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class MeasurementScheduleDaysTests {

	/** 2026-11-19 03:18 MST — 첫날의 생성 시각 1분 뒤 */
	private static final Instant FIRST_DAY = Instant.parse("2026-11-19T10:18:00Z");

	@Autowired
	MeasurementClockAdministration clock;

	@Autowired
	DigestScheduling scheduling;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	JdbcClient jdbc;

	@Test
	void eachDayAfterAdvancingMakesOneDigestWhosePeriodContinuesTheLastOne() {
		UserId daily = user("measure-days-daily@example.com", "03:17");
		UserId evening = user("measure-days-evening@example.com", "21:00");

		assertThat(this.clock.advance(null, FIRST_DAY)).isInstanceOf(ClockAdvanceResult.Advanced.class);
		List<String> dates = List.of("2026-11-19", "2026-11-20", "2026-11-21", "2026-11-22", "2026-11-23",
				"2026-11-24", "2026-11-25");
		for (int day = 0; day < dates.size(); day++) {
			if (day > 0) {
				assertThat(this.clock.advance(Duration.ofHours(24), null)).isInstanceOf(ClockAdvanceResult.Advanced.class);
			}
			assertThat(this.scheduling.runRound()).as(dates.get(day)).isEqualTo(1);
			assertThat(this.scheduling.runRound()).as(dates.get(day) + " 두 번째 회차").isZero();
			Map<String, Object> made = rows(daily).getLast();
			assertThat(made).containsEntry("digest_date", java.sql.Date.valueOf(dates.get(day)))
				.containsEntry("trigger", "SCHEDULED")
				.containsEntry("period_start", Timestamp.from(Instant.parse("2026-11-18T10:17:00Z").plus(Duration.ofDays(day))))
				.containsEntry("period_end", Timestamp.from(Instant.parse("2026-11-19T10:17:00Z").plus(Duration.ofDays(day))));
			ready((Long) made.get("id"));
		}

		assertThat(rows(daily)).hasSize(7);
		assertThat(rows(evening)).isEmpty();
	}

	/** 일정 회차가 만든 행을 생성이 끝난 것처럼 READY 로 끝낸다 */
	private void ready(long id) {
		this.jdbc.sql("""
				update digests set status = 'READY', started_at = now(), completed_at = now() where id = :id
				""").param("id", id).update();
	}

	private List<Map<String, Object>> rows(UserId user) {
		return this.jdbc.sql("""
				select id, digest_date, trigger, period_start, period_end from digests where user_id = :user
				order by digest_date
				""").param("user", user.value()).query().listOfRows();
	}

	/** 쓰는 계정 — `America/Denver` · 주어진 생성 시각, OpenAI 유효 키 */
	private UserId user(String email, String digestTime) {
		UserId user = new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values (:email, '사용자', 'USER', 'ACTIVE', now(), 0) returning id
				""").param("email", email).query(Long.class).single());
		this.jdbc.sql("""
				insert into user_settings (user_id, tag_set_id, timezone, digest_time, weekly_report,
				    weekly_report_day, monthly_report, monthly_report_day, candidate_volume, big_slots, summary_length, reasoning_effort,
				    updated_at, version)
				values (:user, :tagSet, 'America/Denver', cast(:time as time), true, 1, true, 1, 'NORMAL', 3, 'NORMAL',
				    'MEDIUM', now(), 0)
				""")
			.param("user", user.value())
			.param("tagSet", this.tagSets.activeTagSet().id().value())
			.param("time", digestTime)
			.update();
		this.jdbc.sql("""
				insert into llm_credentials (user_id, provider, ciphertext, encrypted_dek, kek_version, key_hint, status,
				  validated_at, created_at)
				values (:user, 'openai', '\\x01'::bytea, '\\x01'::bytea, 1, '', 'VALID', now(), now())
				""").param("user", user.value()).update();
		return user;
	}

}
