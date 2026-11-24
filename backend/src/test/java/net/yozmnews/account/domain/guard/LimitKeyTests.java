package net.yozmnews.account.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;

import net.yozmnews.platform.GuardedAction;
import org.junit.jupiter.api.Test;

/**
 * 요청 제한 기록의 키 — 동작 · 축 · 대상의 SHA-256. 셋 가운데 하나만 달라도 다른 키다.
 * <p>
 * 지키는 규칙: user-R39
 */
class LimitKeyTests {

	@Test
	void isTheSha256OfActionAxisAndSubjectOnSeparateLines() {
		// printf 'SIGN_IN\nCLIENT_ADDRESS\n192.0.2.1' | sha256sum
		assertThat(HexFormat.of()
			.formatHex(LimitKey.of(GuardedAction.SIGN_IN, LimitAxis.CLIENT_ADDRESS, "192.0.2.1").digest()))
			.isEqualTo("1acbdcb3bfba91f809bd1f38235e378e2f58bb64745f2bae0c30d43ff16128fb");
	}

	@Test
	void anyPartChangesTheKey() {
		LimitKey key = LimitKey.of(GuardedAction.LLM_KEY_CHECK, LimitAxis.CLIENT_ADDRESS, "7");

		assertThat(LimitKey.of(GuardedAction.LLM_KEY_CHECK, LimitAxis.CLIENT_ADDRESS, "7")).isEqualTo(key);
		assertThat(LimitKey.of(GuardedAction.LLM_KEY_CHECK, LimitAxis.SESSION_USER, "7")).isNotEqualTo(key);
		assertThat(LimitKey.of(GuardedAction.SIGN_UP, LimitAxis.CLIENT_ADDRESS, "7")).isNotEqualTo(key);
		assertThat(LimitKey.of(GuardedAction.LLM_KEY_CHECK, LimitAxis.CLIENT_ADDRESS, "8")).isNotEqualTo(key);
	}

}
