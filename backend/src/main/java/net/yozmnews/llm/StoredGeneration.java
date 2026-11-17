package net.yozmnews.llm;

import java.util.Objects;

/**
 * 저장한 키의 생성 호출 결과 — docs/specs/llm.md §3
 */
public sealed interface StoredGeneration {

	/** 그 사용자 · 공급자의 저장한 키가 없다 — 공급자에 보내지 않았다 */
	record Missing() implements StoredGeneration {
	}

	/** 저장한 키로 생성을 시도했다 */
	record Done(GenerationResult result) implements StoredGeneration {

		public Done {
			Objects.requireNonNull(result, "result");
		}

	}

}
