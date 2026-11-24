package net.yozmnews.user.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/**
 * 관심 설정의 값 — 시간대 ID, 분 단위 생성 시각.
 * <p>
 * 지키는 규칙: user-R50
 */
class InterestValuesTests {

	@Test
	void timezonesAreAvailableZoneIdsOnly() {
		assertThat(Timezones.parse("Asia/Seoul")).contains(ZoneId.of("Asia/Seoul"));
		assertThat(Timezones.parse("+09:00")).isEmpty();
		assertThat(Timezones.parse("Z")).isEmpty();
		assertThat(Timezones.parse("KST")).isEmpty();
		assertThat(Timezones.parse("asia/seoul")).isEmpty();
	}

	@Test
	void theDigestTimeIsInMinutes() {
		assertThat(new DigestSchedule(ZoneId.of("Asia/Seoul"), LocalTime.of(7, 0)).time()).isEqualTo(LocalTime.of(7, 0));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new DigestSchedule(ZoneId.of("Asia/Seoul"), LocalTime.of(7, 0, 30)));
	}

}
