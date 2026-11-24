package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.AccountDirectory;
import net.yozmnews.account.application.ProvisionedAccounts;
import net.yozmnews.account.domain.account.Onboarding;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.AccountEvents;
import net.yozmnews.user.ReportScheduleDirectory;
import net.yozmnews.user.ReportScheduleEntry;
import net.yozmnews.user.UserTables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 리포트 일정 목록 — 쓰는 계정마다 시간대 · 다이제스트 생성 시각 · 리포트 설정을 ID 순 쪽으로 낸다. 설정 행이 없는 계정은 기본값이고, 정지 ·
 * 탈퇴 계정은 빠진다. 실제 PostgreSQL.
 * <p>
 * 이 시험의 계정은 마지막에 만들어 ID 가 가장 크다 — 쪽의 시작을 이 시험의 첫 계정 바로 앞으로 두면 다른 시험이 남긴 계정이 섞이지 않는다.
 * 기대값은 손으로 쓴 리터럴이다(계정을 만들 때의 기본값 — `Asia/Seoul` · 07:00 · 주간 켬 · 월요일 · 월간 켬 · 1일).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ReportScheduleDirectoryTests {

	private static final Instant NOW = Instant.parse("2026-11-25T03:00:00Z");

	@Autowired
	ReportScheduleDirectory directory;

	@Autowired
	ApplicationContext context;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		AccountEvents.awaitCreatedHandled(this.jdbc);
		UserTables.deleteByEmail(this.jdbc, "report-schedule-%");
	}

	@Test
	void activeAccountsComeInIdOrderWithTheDefaultsWhenTheirSettingsRowIsMissing() {
		UserId alice = account("report-schedule-alice@example.com");
		UserId suspended = account("report-schedule-suspended@example.com");
		UserId withdrawn = account("report-schedule-withdrawn@example.com");
		UserId unset = account("report-schedule-unset@example.com");
		UserId bob = account("report-schedule-bob@example.com");
		this.jdbc.sql("update users set status = 'SUSPENDED' where id = :id").param("id", suspended.value()).update();
		this.jdbc.sql("update users set status = 'DELETED' where id = :id").param("id", withdrawn.value()).update();
		this.jdbc.sql("delete from user_settings where user_id = :id").param("id", unset.value()).update();
		this.jdbc.sql("""
				update user_settings set timezone = 'America/New_York', digest_time = '23:30', weekly_report = false,
				  weekly_report_day = 5, monthly_report = true, monthly_report_day = 31
				where user_id = :id""").param("id", bob.value()).update();

		List<ReportScheduleEntry> page = this.directory.page(alice.value() - 1, ReportScheduleDirectory.MAX_PAGE);

		assertThat(page).containsExactly(
				new ReportScheduleEntry(alice, ZoneId.of("Asia/Seoul"), LocalTime.of(7, 0), true, DayOfWeek.MONDAY,
						true, 1),
				new ReportScheduleEntry(unset, ZoneId.of("Asia/Seoul"), LocalTime.of(7, 0), true, DayOfWeek.MONDAY,
						true, 1),
				new ReportScheduleEntry(bob, ZoneId.of("America/New_York"), LocalTime.of(23, 30), false,
						DayOfWeek.FRIDAY, true, 31));
	}

	@Test
	void aPageStopsAtTheLimitAndTheNextPageStartsAfterItsLastUser() {
		UserId first = account("report-schedule-first@example.com");
		UserId second = account("report-schedule-second@example.com");
		UserId third = account("report-schedule-third@example.com");

		List<ReportScheduleEntry> page = this.directory.page(first.value() - 1, 2);
		List<ReportScheduleEntry> next = this.directory.page(second.value(), 2);

		assertThat(page).extracting(ReportScheduleEntry::user).containsExactly(first, second);
		assertThat(next).extracting(ReportScheduleEntry::user).containsExactly(third);
	}

	@Test
	void aPageIsNeverLargerThanAnAccountPage() {
		// 일정 목록의 쪽은 계정 공개 조회의 쪽 하나로 읽는다 — 쪽 상한이 계정 쪽 상한을 넘으면 계정 조회가 거부한다
		assertThat(ReportScheduleDirectory.MAX_PAGE).isLessThanOrEqualTo(AccountDirectory.MAX_PAGE);
	}

	@Test
	void refusesAPageSizeOutsideOneToTheLimit() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.page(0, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.page(0, 501));
		// 대조 — 경계 값은 받는다
		assertThat(this.directory.page(Long.MAX_VALUE - 1, 1)).isEmpty();
		assertThat(this.directory.page(Long.MAX_VALUE - 1, 500)).isEmpty();
	}

	private UserId account(String email) {
		UserId user = ProvisionedAccounts.provision(this.context, Onboarding.OPEN_SIGN_UP, email, "사용자",
				"correct horse battery", NOW).orElseThrow();
		AccountEvents.awaitCreatedHandled(this.jdbc);
		return user;
	}

}
