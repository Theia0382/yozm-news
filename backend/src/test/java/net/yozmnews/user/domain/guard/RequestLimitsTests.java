package net.yozmnews.user.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import net.yozmnews.platform.GuardedAction;
import org.junit.jupiter.api.Test;

/**
 * 요청 제한 정책 표 — 동작마다 축 · 한도 · 창. 로그인 · 복구는 클라이언트 주소로만 센다(사용자 축은 실패 지연이 맡는다).
 * <p>
 * 지키는 규칙: user-R38
 */
class RequestLimitsTests {

	@Test
	void signInCountsThirtyPerTenMinutesByAddressOnly() {
		assertThat(RequestLimits.rulesOf(GuardedAction.SIGN_IN))
			.containsExactly(new LimitRule(LimitAxis.CLIENT_ADDRESS, 30, Duration.parse("PT10M")));
	}

	@Test
	void accountRecoveryCountsTenPerHourByAddressOnly() {
		assertThat(RequestLimits.rulesOf(GuardedAction.ACCOUNT_RECOVERY))
			.containsExactly(new LimitRule(LimitAxis.CLIENT_ADDRESS, 10, Duration.parse("PT1H")));
	}

	@Test
	void invitationCheckCountsThirtyPerHourByAddress() {
		assertThat(RequestLimits.rulesOf(GuardedAction.INVITATION_CHECK))
			.containsExactly(new LimitRule(LimitAxis.CLIENT_ADDRESS, 30, Duration.parse("PT1H")));
	}

	@Test
	void invitationAcceptanceCountsTenPerHourByAddress() {
		assertThat(RequestLimits.rulesOf(GuardedAction.INVITATION_ACCEPTANCE))
			.containsExactly(new LimitRule(LimitAxis.CLIENT_ADDRESS, 10, Duration.parse("PT1H")));
	}

	@Test
	void signUpCountsFivePerHourByAddress() {
		assertThat(RequestLimits.rulesOf(GuardedAction.SIGN_UP))
			.containsExactly(new LimitRule(LimitAxis.CLIENT_ADDRESS, 5, Duration.parse("PT1H")));
	}

	@Test
	void llmKeyCheckCountsTwentyPerUserAndSixtyPerAddressEachHour() {
		assertThat(RequestLimits.rulesOf(GuardedAction.LLM_KEY_CHECK)).containsExactly(
				new LimitRule(LimitAxis.SESSION_USER, 20, Duration.parse("PT1H")),
				new LimitRule(LimitAxis.CLIENT_ADDRESS, 60, Duration.parse("PT1H")));
	}

	@Test
	void feedbackCountsSevenHundredFiftyPerUserEachTenMinutes() {
		// 다이제스트 하나의 항목(많아야 최대 후보 수 — 직접 입력 상한 100, 상한을 낮추기 전에 저장된 값은 150 까지)을 누르고 고쳐 누르는
		// 사용이 창 하나에 든다 — 항목마다 다섯 번까지 덮는다
		assertThat(RequestLimits.rulesOf(GuardedAction.FEEDBACK))
			.containsExactly(new LimitRule(LimitAxis.SESSION_USER, 750, Duration.parse("PT10M")));
	}

	@Test
	void ruleRejectsAWindowThatDoesNotDivideTheDayAndAnEmptyLimit() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new LimitRule(LimitAxis.CLIENT_ADDRESS, 1, Duration.parse("PT7M")));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new LimitRule(LimitAxis.CLIENT_ADDRESS, 1, Duration.parse("PT0.5S")));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new LimitRule(LimitAxis.CLIENT_ADDRESS, 0, Duration.parse("PT1H")));
	}

}
