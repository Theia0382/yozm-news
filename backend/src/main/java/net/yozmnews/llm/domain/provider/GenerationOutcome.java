package net.yozmnews.llm.domain.provider;

import java.time.Duration;
import java.util.Objects;

import net.yozmnews.llm.GenerationStop;
import net.yozmnews.llm.LlmFailureKind;
import org.jspecify.annotations.Nullable;

/**
 * 생성 요청 하나의 결과 — docs/specs/llm.md §4 "나가는 쪽"
 */
public sealed interface GenerationOutcome {

	/**
	 * 공급자가 답했다.
	 *
	 * @param text 텍스트 블록을 이은 본문 — 비었을 수 있다
	 * @param inputTokens 공급자가 센 입력 토큰
	 * @param outputTokens 공급자가 센 출력 토큰(추론 포함)
	 */
	record Answered(String text, int inputTokens, int outputTokens, GenerationStop stop) implements GenerationOutcome {

		public Answered {
			Objects.requireNonNull(text, "text");
			Objects.requireNonNull(stop, "stop");
			if (inputTokens < 0 || outputTokens < 0) {
				throw new IllegalArgumentException("토큰 수는 0 이상이다");
			}
		}

	}

	/**
	 * 답하지 못했다.
	 *
	 * @param message 고정 문구 · 상태 코드 · 예외 클래스 이름뿐이다(llm-R9)
	 * @param retryAfter 공급자가 준 기다릴 시간 — 없으면 null
	 * @param deadlinePassed 응답 마감을 넘겼는가
	 */
	record Failed(LlmFailureKind kind, String message, @Nullable Duration retryAfter, boolean deadlinePassed)
			implements GenerationOutcome {

		public Failed {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(message, "message");
		}

	}

}
