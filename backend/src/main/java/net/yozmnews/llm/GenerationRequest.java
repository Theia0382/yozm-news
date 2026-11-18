package net.yozmnews.llm;

import java.util.Objects;

import net.yozmnews.llm.domain.schema.SchemaDocuments;
import org.jspecify.annotations.Nullable;

/**
 * 생성 호출의 요청 — docs/specs/llm.md §3.
 * <p>
 * 과제의 것만 담는다 — 출력 스키마는 표준 JSON Schema 문서 그대로, 추론 강도는 공급자와 무관한 단계, 출력 토큰은 본문 몫만. 공급자의 꼴로
 * 옮기는 것과 추론 몫을 더하는 것은 관문이 한다.
 *
 * @param model 쓸 모델 — 공급자 · 모델 ID · 구조화 출력 지원 여부
 * @param instructions 지시 구획(공급자의 system)
 * @param userContent 사용자 구획(공급자의 user 메시지)
 * @param outputSchema 출력 JSON Schema 문서(JSON 객체) — 없으면 null
 * @param effort 추론 강도
 * @param maxBodyTokens 본문 몫의 출력 토큰 상한 — 1 ~ {@value #MAX_BODY_TOKENS}
 */
public record GenerationRequest(LlmModel model, String instructions, String userContent,
		@Nullable String outputSchema, ReasoningEffort effort, int maxBodyTokens) {

	/**
	 * 본문 몫 출력 토큰 상한의 최댓값 — 측정 전의 잠정값이다. 다이제스트 한 편의 본문 토큰을 실측해 고친다.
	 */
	public static final int MAX_BODY_TOKENS = 32_000;

	public GenerationRequest {
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(instructions, "instructions");
		Objects.requireNonNull(userContent, "userContent");
		Objects.requireNonNull(effort, "effort");
		if (instructions.isBlank()) {
			throw new IllegalArgumentException("지시가 비었다");
		}
		if (userContent.isBlank()) {
			throw new IllegalArgumentException("사용자 구획이 비었다");
		}
		if (maxBodyTokens < 1 || maxBodyTokens > MAX_BODY_TOKENS) {
			throw new IllegalArgumentException(
					"본문 토큰 상한은 1 ~ " + MAX_BODY_TOKENS + " 이다 — " + maxBodyTokens);
		}
		if (outputSchema != null && !SchemaDocuments.isObject(outputSchema)) {
			throw new IllegalArgumentException("출력 스키마는 JSON 객체 문서다");
		}
	}

}
