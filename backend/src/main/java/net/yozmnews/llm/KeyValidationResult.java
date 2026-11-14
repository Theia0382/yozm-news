package net.yozmnews.llm;

import java.util.Objects;

/**
 * 키 검증의 결과 — docs/specs/llm.md §3 · llm-R6 · R8 · R9
 */
public sealed interface KeyValidationResult {

	/** 공급자가 그 키의 요청에 정상으로 답했다 — 잔액 · 지출 한도와 특정 모델을 쓸 권한은 확인하지 않는다 */
	record Valid() implements KeyValidationResult {

	}

	/**
	 * 답하지 못했다.
	 *
	 * @param kind 까닭의 종류
	 * @param message 운영 로그용 문구 — 고정 문구 · 상태 코드 · 예외 클래스 이름뿐이다
	 */
	record Rejected(LlmFailureKind kind, String message) implements KeyValidationResult {

		public Rejected {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(message, "message");
		}

	}

}
