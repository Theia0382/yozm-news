package net.yozmnews.user.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/**
 * 관심 설정의 값 — 자유 텍스트 관심사의 상한(코드 포인트), 시간대 ID, 분 단위 생성 시각.
 * <p>
 * 지키는 규칙: user-R50
 */
class InterestValuesTests {

	@Test
	void interestTextIsStrippedAndUpToFiveHundredCodePoints() {
		assertThat(InterestText.parse("  반도체와 전력망  ")).contains(new InterestText("반도체와 전력망"));
		assertThat(InterestText.parse("가".repeat(500))).isPresent();
		assertThat(InterestText.parse("가".repeat(501))).isEmpty();
		assertThat(InterestText.parse(" ".repeat(600))).contains(new InterestText(""));
	}

	@Test
	void interestTextCountsCodePointsNotChars() {
		// U+1F600 은 char 둘 · 코드 포인트 하나다
		String emoji = new String(Character.toChars(0x1F600));
		assertThat(InterestText.parse(emoji.repeat(500))).isPresent();
		assertThat(InterestText.parse(emoji.repeat(501))).isEmpty();
	}

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
