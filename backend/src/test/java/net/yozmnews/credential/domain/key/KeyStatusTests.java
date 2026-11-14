package net.yozmnews.credential.domain.key;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.llm.KeyValidationResult;
import net.yozmnews.llm.LlmFailureKind;
import org.junit.jupiter.api.Test;

/**
 * 검증 결과에서 다음 상태 — 손으로 적은 전이 표와 견준다. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: credential-R14
 */
class KeyStatusTests {

	@Test
	void aSuccessMakesTheKeyValid() {
		assertThat(KeyStatus.after(new KeyValidationResult.Valid())).contains(KeyStatus.VALID);
	}

	@Test
	void anInvalidOrDeniedKeyBecomesInvalid() {
		assertThat(KeyStatus.after(rejected(LlmFailureKind.INVALID_KEY))).contains(KeyStatus.INVALID);
		assertThat(KeyStatus.after(rejected(LlmFailureKind.PERMISSION_DENIED))).contains(KeyStatus.INVALID);
	}

	@Test
	void quotaTransientAndUnexpectedFailuresLeaveTheStatus() {
		assertThat(KeyStatus.after(rejected(LlmFailureKind.QUOTA_EXHAUSTED))).isEmpty();
		assertThat(KeyStatus.after(rejected(LlmFailureKind.TRANSIENT))).isEmpty();
		assertThat(KeyStatus.after(rejected(LlmFailureKind.UNEXPECTED))).isEmpty();
	}

	@Test
	void coversEveryFailureKind() {
		// 종류가 늘면 위 표에 줄을 더한다
		assertThat(LlmFailureKind.values()).containsExactly(LlmFailureKind.INVALID_KEY,
				LlmFailureKind.PERMISSION_DENIED, LlmFailureKind.QUOTA_EXHAUSTED, LlmFailureKind.TRANSIENT,
				LlmFailureKind.UNEXPECTED);
	}

	private static KeyValidationResult rejected(LlmFailureKind kind) {
		return new KeyValidationResult.Rejected(kind, "시험 문구");
	}

}
