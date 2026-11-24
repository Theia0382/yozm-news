package net.yozmnews.account.domain.humancheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * 봇 차단 설정의 판정 — 없음 · 빈 값 · off 는 끔, turnstile 은 사이트 키가 있어야 한다. 실패 문구에 값을 싣지 않는다.
 * <p>
 * 지키는 규칙: account-R33
 */
class HumanCheckSettingTests {

	@Test
	void missingBlankAndOffTurnItOff() {
		assertThat(HumanCheckSetting.of(null, null)).isEqualTo(new HumanCheckSetting.Off());
		assertThat(HumanCheckSetting.of("", "1x00000000000000000000AA")).isEqualTo(new HumanCheckSetting.Off());
		assertThat(HumanCheckSetting.of("off", null)).isEqualTo(new HumanCheckSetting.Off());
		assertThat(HumanCheckSetting.of("OFF", null)).isEqualTo(new HumanCheckSetting.Off());
	}

	@Test
	void turnstileWithASiteKeyTurnsItOn() {
		assertThat(HumanCheckSetting.of("turnstile", "1x00000000000000000000AA"))
			.isEqualTo(new HumanCheckSetting.Turnstile("1x00000000000000000000AA"));
		assertThat(HumanCheckSetting.of(" Turnstile ", " 1x00000000000000000000AA "))
			.isEqualTo(new HumanCheckSetting.Turnstile("1x00000000000000000000AA"));
	}

	@Test
	void turnstileWithoutASiteKeyNamesTheVariable() {
		assertThatIllegalArgumentException().isThrownBy(() -> HumanCheckSetting.of("turnstile", " "))
			.withMessageContaining("YOZMNEWS_HUMAN_CHECK_TURNSTILE_SITE_KEY");
	}

	@Test
	void anUnknownProviderIsRejectedWithoutQuotingIt() {
		assertThatIllegalArgumentException().isThrownBy(() -> HumanCheckSetting.of("hcaptcha-secret-looking", null))
			.withMessageContaining("YOZMNEWS_HUMAN_CHECK_PROVIDER")
			.withMessageNotContaining("hcaptcha-secret-looking");
	}

}
