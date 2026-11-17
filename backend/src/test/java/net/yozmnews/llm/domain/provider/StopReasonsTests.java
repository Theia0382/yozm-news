package net.yozmnews.llm.domain.provider;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.llm.GenerationStop;
import org.junit.jupiter.api.Test;

/**
 * 공급자의 종료 사유 — OpenAI 는 Spring AI 가 넘기는 SDK 열거 이름, Anthropic 은 공급자 원문을 가른다.
 * <p>
 * 지키는 규칙: llm-R22
 */
class StopReasonsTests {

	@Test
	void openAi() {
		assertThat(StopReasons.openAi("STOP", false)).contains(GenerationStop.COMPLETE);
		assertThat(StopReasons.openAi("LENGTH", false)).contains(GenerationStop.TRUNCATED);
		assertThat(StopReasons.openAi("CONTENT_FILTER", false)).contains(GenerationStop.REFUSED);
		assertThat(StopReasons.openAi("STOP", true)).contains(GenerationStop.REFUSED);
		assertThat(StopReasons.openAi(null, true)).contains(GenerationStop.REFUSED);
	}

	@Test
	void openAiDoesNotKnowTheRest() {
		assertThat(StopReasons.openAi("TOOL_CALLS", false)).isEmpty();
		assertThat(StopReasons.openAi("_UNKNOWN", false)).isEmpty();
		assertThat(StopReasons.openAi("stop", false)).isEmpty();
		assertThat(StopReasons.openAi(null, false)).isEmpty();
	}

	@Test
	void anthropic() {
		assertThat(StopReasons.anthropic("end_turn")).contains(GenerationStop.COMPLETE);
		assertThat(StopReasons.anthropic("stop_sequence")).contains(GenerationStop.COMPLETE);
		assertThat(StopReasons.anthropic("max_tokens")).contains(GenerationStop.TRUNCATED);
		assertThat(StopReasons.anthropic("model_context_window_exceeded")).contains(GenerationStop.TRUNCATED);
		assertThat(StopReasons.anthropic("refusal")).contains(GenerationStop.REFUSED);
	}

	@Test
	void anthropicDoesNotKnowTheRest() {
		assertThat(StopReasons.anthropic("tool_use")).isEmpty();
		assertThat(StopReasons.anthropic("pause_turn")).isEmpty();
		assertThat(StopReasons.anthropic("END_TURN")).isEmpty();
		assertThat(StopReasons.anthropic(null)).isEmpty();
	}

}
