package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.yozmnews.platform.GuardedAction;
import net.yozmnews.user.domain.guard.ClientAddress;
import net.yozmnews.user.domain.humancheck.HumanCheckResult;
import net.yozmnews.user.domain.humancheck.HumanCheckSetting;
import net.yozmnews.user.domain.humancheck.HumanCheckToken;
import net.yozmnews.user.domain.humancheck.HumanVerifier;
import org.junit.jupiter.api.Test;

/**
 * 봇 차단 유스케이스 — 켬 · 끔, 읽지 못하는 토큰은 검증기에 묻지 않고 거부, 켰는데 검증기가 없으면 설정 이름을 말하고 멈춘다.
 * <p>
 * 지키는 규칙: user-R41
 */
class HumanCheckTests {

	private static final ClientAddress CLIENT = ClientAddress.parse("192.0.2.1").orElseThrow();

	@Test
	void offIsDisabledAndNeedsNoVerifier() {
		assertThat(new HumanCheck(new HumanCheckSetting.Off(), Optional.empty()).enabled()).isFalse();
	}

	@Test
	void turnstileWithAVerifierIsEnabledAndAsksIt() {
		RecordingVerifier verifier = new RecordingVerifier();
		HumanCheck check = new HumanCheck(new HumanCheckSetting.Turnstile("1x00000000000000000000AA"),
				Optional.of(verifier));

		assertThat(check.enabled()).isTrue();
		assertThat(check.verify("0.token", GuardedAction.SIGN_UP, CLIENT)).isEqualTo(new HumanCheckResult.Passed());
		assertThat(verifier.actions).containsExactly(GuardedAction.SIGN_UP);
	}

	@Test
	void unreadableTokensAreRejectedWithoutAskingTheVerifier() {
		RecordingVerifier verifier = new RecordingVerifier();
		HumanCheck check = new HumanCheck(new HumanCheckSetting.Turnstile("1x00000000000000000000AA"),
				Optional.of(verifier));

		assertThat(check.verify(null, GuardedAction.SIGN_IN, CLIENT))
			.isEqualTo(new HumanCheckResult.Rejected("token-unreadable"));
		assertThat(check.verify("", GuardedAction.SIGN_IN, CLIENT))
			.isEqualTo(new HumanCheckResult.Rejected("token-unreadable"));
		assertThat(check.verify("x".repeat(2049), GuardedAction.SIGN_IN, CLIENT))
			.isEqualTo(new HumanCheckResult.Rejected("token-unreadable"));
		assertThat(verifier.actions).isEmpty();
	}

	@Test
	void offRefusesToVerifyEvenWithAVerifierAround() {
		// 끈 설정에도 검증기가 있을 수 있다 — 로컬 개발의 항상 통과 검증기, 비밀 키만 둔 설정
		RecordingVerifier verifier = new RecordingVerifier();
		HumanCheck check = new HumanCheck(new HumanCheckSetting.Off(), Optional.of(verifier));

		assertThatIllegalStateException().isThrownBy(() -> check.verify("0.token", GuardedAction.SIGN_IN, CLIENT));
		assertThat(verifier.actions).isEmpty();
	}

	@Test
	void turnstileWithoutAVerifierStopsNamingTheSecretKey() {
		assertThatIllegalStateException()
			.isThrownBy(() -> new HumanCheck(new HumanCheckSetting.Turnstile("1x00000000000000000000AA"),
					Optional.empty()))
			.withMessageContaining("yozmnews.human-check.turnstile.secret-key")
			.withMessageContaining("YOZMNEWS_HUMAN_CHECK_TURNSTILE_SECRET_KEY");
	}

	/** 물은 동작을 남기고 통과시키는 검증기 */
	private static final class RecordingVerifier implements HumanVerifier {

		private final List<GuardedAction> actions = new ArrayList<>();

		@Override
		public HumanCheckResult verify(HumanCheckToken token, GuardedAction action, ClientAddress client) {
			this.actions.add(action);
			return new HumanCheckResult.Passed();
		}

	}

}
