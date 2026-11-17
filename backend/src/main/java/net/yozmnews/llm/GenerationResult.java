package net.yozmnews.llm;

import java.time.Duration;
import java.util.Objects;

/**
 * 생성 호출의 결과 — docs/specs/llm.md §3.
 * <p>
 * 실패 종류는 키 검증과 같은 다섯이다. 출력을 파싱 · 검증하지 않는다 — 부르는 쪽의 것이다.
 */
public sealed interface GenerationResult {

	/** 관문 안에서 든 시간 — 다시 보내기까지 기다린 시간을 포함한다 */
	Duration elapsed();

	/**
	 * 공급자가 답했다.
	 *
	 * @param text 응답 본문 — 텍스트 블록을 이은 것. 잘리거나 거절된 응답이면 비었을 수 있다
	 * @param inputTokens 공급자가 센 입력 토큰 — 마지막으로 보낸 요청의 값
	 * @param outputTokens 공급자가 센 출력 토큰 — 추론 토큰을 포함한다. 마지막으로 보낸 요청의 값
	 * @param stop 종료 사유
	 * @param schemaEnforced 공급자에게 출력 스키마를 강제 인자로 보냈는가 — 스키마가 있고 모델이 구조화 출력을 지원할 때만 참이다
	 */
	record Generated(String text, int inputTokens, int outputTokens, Duration elapsed, GenerationStop stop,
			boolean schemaEnforced) implements GenerationResult {

		public Generated {
			Objects.requireNonNull(text, "text");
			Objects.requireNonNull(elapsed, "elapsed");
			Objects.requireNonNull(stop, "stop");
			if (inputTokens < 0 || outputTokens < 0) {
				throw new IllegalArgumentException("토큰 수는 0 이상이다");
			}
			if (elapsed.isNegative()) {
				throw new IllegalArgumentException("소요 시간은 0 이상이다");
			}
		}

	}

	/**
	 * 공급자가 답하지 못했다.
	 *
	 * @param kind 까닭의 종류
	 * @param message 운영 로그용 문구 — 고정 문구 · 상태 코드 · 예외 클래스 이름뿐이다
	 */
	record Failed(LlmFailureKind kind, String message, Duration elapsed) implements GenerationResult {

		public Failed {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(message, "message");
			Objects.requireNonNull(elapsed, "elapsed");
			if (elapsed.isNegative()) {
				throw new IllegalArgumentException("소요 시간은 0 이상이다");
			}
		}

	}

}
