package net.yozmnews.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * 생성 호출의 요청과 결과의 값 — 빈 지시 · 사용자 구획, 본문 토큰 상한의 범위, JSON 객체가 아닌 스키마를 거부한다.
 * <p>
 * 지키는 규칙: llm Spec §3 GenerationRequest · GenerationResult
 */
class GenerationRequestTests {

	private static final LlmModel MODEL = new LlmModel(Provider.ANTHROPIC, "claude-x", 32_000, true);

	@Test
	void takesTheBoundsOfTheBodyTokens() {
		assertThat(request("{}", 1).maxBodyTokens()).isEqualTo(1);
		assertThat(request("{}", 32_000).maxBodyTokens()).isEqualTo(32_000);
	}

	@Test
	void refusesBodyTokensOutOfBounds() {
		assertThatThrownBy(() -> request("{}", 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> request("{}", 32_001)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void refusesBlankInstructionsAndUserContent() {
		assertThatThrownBy(() -> new GenerationRequest(MODEL, " \n", "사용자", null, ReasoningEffort.LOW, 100))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new GenerationRequest(MODEL, "지시", "\t", null, ReasoningEffort.LOW, 100))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void takesAnObjectSchemaOrNone() {
		assertThat(request("{\"type\":\"object\"}", 100).outputSchema()).isEqualTo("{\"type\":\"object\"}");
		assertThat(request(null, 100).outputSchema()).isNull();
	}

	@Test
	void refusesASchemaThatIsNotAJsonObject() {
		assertThatThrownBy(() -> request("[]", 100)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> request("\"object\"", 100)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> request("{\"type\":", 100)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> request("{} []", 100)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> request("", 100)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void refusesAMissingModelOrEffort() {
		assertThatThrownBy(() -> new GenerationRequest(null, "지시", "사용자", null, ReasoningEffort.LOW, 100))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new GenerationRequest(MODEL, "지시", "사용자", null, null, 100))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	void refusesNegativeTokensAndElapsedInResults() {
		assertThatThrownBy(() -> new GenerationResult.Generated("", -1, 0, Duration.ZERO, GenerationStop.COMPLETE,
				false))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new GenerationResult.Generated("", 0, 0, Duration.ofMillis(-1),
				GenerationStop.COMPLETE, false))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new GenerationResult.Failed(LlmFailureKind.TRANSIENT, "문구", Duration.ofMillis(-1)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(new GenerationResult.Generated("", 0, 0, Duration.ZERO, GenerationStop.TRUNCATED, false).text())
			.isEmpty();
	}

	private static GenerationRequest request(String schema, int bodyTokens) {
		return new GenerationRequest(MODEL, "지시", "사용자", schema, ReasoningEffort.MEDIUM, bodyTokens);
	}

}
