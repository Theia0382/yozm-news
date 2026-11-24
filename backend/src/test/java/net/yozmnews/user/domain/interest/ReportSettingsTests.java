package net.yozmnews.user.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.DayOfWeek;

import org.junit.jupiter.api.Test;

/**
 * 리포트 설정 — 월간 날짜는 1 ~ 31 이다(그 날이 없는 달의 경계는 리포트의 일정이 정한다). 기대값은 손으로 쓴 리터럴이다.
 */
class ReportSettingsTests {

	@Test
	void theMonthlyDayIsFromTheFirstToTheThirtyFirst() {
		assertThat(ReportSettings.validMonthlyDay(1)).isTrue();
		assertThat(ReportSettings.validMonthlyDay(31)).isTrue();
		assertThat(ReportSettings.validMonthlyDay(0)).isFalse();
		assertThat(ReportSettings.validMonthlyDay(32)).isFalse();

		assertThat(new ReportSettings(true, DayOfWeek.MONDAY, true, 31).monthlyDay()).isEqualTo(31);
		assertThatIllegalArgumentException().isThrownBy(() -> new ReportSettings(true, DayOfWeek.MONDAY, true, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new ReportSettings(true, DayOfWeek.MONDAY, true, 32));
	}

	@Test
	void theDefaultsAreWeeklyOnMondayAndMonthlyOnTheFirst() {
		assertThat(InterestDefaults.REPORTS).isEqualTo(new ReportSettings(true, DayOfWeek.MONDAY, true, 1));
		assertThat(ScheduleSettings.DEFAULTS.reports()).isEqualTo(InterestDefaults.REPORTS);
	}

}
