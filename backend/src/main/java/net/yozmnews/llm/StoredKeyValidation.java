package net.yozmnews.llm;

import java.util.Objects;

/**
 * 저장한 키의 검증 결과 — docs/specs/llm.md §3 · llm-R17
 */
public sealed interface StoredKeyValidation {

	/** 그 사용자 · 공급자의 저장한 키가 없다 */
	record Missing() implements StoredKeyValidation {
	}

	/** 저장한 키로 물었다 */
	record Checked(KeyValidationResult result) implements StoredKeyValidation {

		public Checked {
			Objects.requireNonNull(result, "result");
		}

	}

}
