package net.yozmnews.llm.domain.provider;

import java.util.Objects;

import net.yozmnews.llm.LlmFailureKind;

/**
 * 확인 요청의 결과 — docs/specs/llm.md §4 "나가는 쪽"
 */
public sealed interface ProbeResult {

	/** 공급자가 그 키의 요청에 정상으로 답했다 */
	record Reachable() implements ProbeResult {

	}

	/**
	 * 답하지 못했다.
	 *
	 * @param message 고정 문구 · 상태 코드 · 예외 클래스 이름뿐이다(llm-R9)
	 */
	record Failed(LlmFailureKind kind, String message) implements ProbeResult {

		public Failed {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(message, "message");
		}

	}

}
