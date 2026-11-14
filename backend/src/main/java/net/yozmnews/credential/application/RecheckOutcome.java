package net.yozmnews.credential.application;

import java.util.Objects;

import net.yozmnews.llm.LlmFailureKind;

/**
 * 다시 검증의 결과 — 키는 확인 뒤에 다시 읽은 지금 행이다(credential-R12).
 */
public sealed interface RecheckOutcome {

	/** 저장한 키가 없다 — 처음부터 없었거나 묻는 사이 지워졌다 */
	record Missing() implements RecheckOutcome {
	}

	/** 공급자가 성공으로 답했다 */
	record Valid(KeyView key) implements RecheckOutcome {

		public Valid {
			Objects.requireNonNull(key, "key");
		}

	}

	/** 공급자가 키를 무효로 만드는 실패로 답했다 */
	record Invalid(LlmFailureKind kind, KeyView key) implements RecheckOutcome {

		public Invalid {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(key, "key");
		}

	}

	/** 상태를 바꾸지 않는 실패다 — 키가 쓸 수 있는지 이번에는 알 수 없다 */
	record Undetermined(LlmFailureKind kind, KeyView key) implements RecheckOutcome {

		public Undetermined {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(key, "key");
		}

	}

}
