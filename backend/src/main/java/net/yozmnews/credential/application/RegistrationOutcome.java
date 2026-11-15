package net.yozmnews.credential.application;

import java.util.Objects;

import net.yozmnews.llm.LlmFailureKind;

/**
 * 키 등록의 결과.
 */
public sealed interface RegistrationOutcome {

	/** 검증을 통과해 저장했다 */
	record Registered(KeyView key) implements RegistrationOutcome {

		public Registered {
			Objects.requireNonNull(key, "key");
		}

	}

	/** 검증에 실패해 저장하지 않았다 */
	record Rejected(LlmFailureKind kind) implements RegistrationOutcome {

		public Rejected {
			Objects.requireNonNull(kind, "kind");
		}

	}

}
