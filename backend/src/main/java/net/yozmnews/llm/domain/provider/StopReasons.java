package net.yozmnews.llm.domain.provider;

import java.util.Optional;

import net.yozmnews.llm.GenerationStop;
import org.jspecify.annotations.Nullable;

/**
 * 공급자의 종료 사유를 {@link GenerationStop} 으로 가르는 정본 — docs/specs/llm.md llm-R22.
 * <p>
 * 입력은 어댑터가 Spring AI 의 생성 메타데이터에서 받는 문자열이다. OpenAI 는 Spring AI 가 SDK 열거 이름(대문자)으로 옮겨 넘기고, Anthropic
 * 은 공급자 원문 그대로다. 모르는 값은 빈 값이다 — 부르는 쪽이 예상 밖의 답으로 다룬다.
 */
public final class StopReasons {

	private StopReasons() {
	}

	/**
	 * @param finishReason Spring AI 가 넘긴 OpenAI 의 종료 사유 — 없으면 null
	 * @param refused 응답에 거절 문구({@code refusal})가 있는가
	 */
	public static Optional<GenerationStop> openAi(@Nullable String finishReason, boolean refused) {
		if (refused) {
			return Optional.of(GenerationStop.REFUSED);
		}
		if (finishReason == null) {
			return Optional.empty();
		}
		return switch (finishReason) {
			case "STOP" -> Optional.of(GenerationStop.COMPLETE);
			case "LENGTH" -> Optional.of(GenerationStop.TRUNCATED);
			case "CONTENT_FILTER" -> Optional.of(GenerationStop.REFUSED);
			default -> Optional.empty();
		};
	}

	/**
	 * @param stopReason Spring AI 가 넘긴 Anthropic 의 종료 사유 — 없으면 null
	 */
	public static Optional<GenerationStop> anthropic(@Nullable String stopReason) {
		if (stopReason == null) {
			return Optional.empty();
		}
		return switch (stopReason) {
			case "end_turn", "stop_sequence" -> Optional.of(GenerationStop.COMPLETE);
			case "max_tokens", "model_context_window_exceeded" -> Optional.of(GenerationStop.TRUNCATED);
			case "refusal" -> Optional.of(GenerationStop.REFUSED);
			default -> Optional.empty();
		};
	}

}
