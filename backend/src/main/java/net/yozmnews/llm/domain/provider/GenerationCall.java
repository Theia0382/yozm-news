package net.yozmnews.llm.domain.provider;

import java.util.Objects;

import net.yozmnews.llm.ReasoningEffort;
import org.jspecify.annotations.Nullable;

/**
 * 포트에 넘기는 생성 요청 하나 — docs/specs/llm.md §4 "나가는 쪽".
 *
 * @param modelId 공급자의 모델 ID
 * @param structuredOutput 그 모델이 구조화 출력을 지원하는가 — 참일 때만 스키마를 강제 인자로 보낸다
 * @param instructions 지시 구획
 * @param userContent 사용자 구획
 * @param outputSchema 부르는 쪽이 넘긴 출력 JSON Schema 원본 — 없으면 null
 * @param effort 추론 강도
 * @param maxOutputTokens 추론을 포함한 출력 토큰 상한 — 언제나 공급자에 명시해 보낸다
 */
public record GenerationCall(String modelId, boolean structuredOutput, String instructions, String userContent,
		@Nullable String outputSchema, ReasoningEffort effort, int maxOutputTokens) {

	public GenerationCall {
		Objects.requireNonNull(modelId, "modelId");
		Objects.requireNonNull(instructions, "instructions");
		Objects.requireNonNull(userContent, "userContent");
		Objects.requireNonNull(effort, "effort");
		if (maxOutputTokens < 1) {
			throw new IllegalArgumentException("출력 토큰 상한은 1 이상이다 — " + maxOutputTokens);
		}
	}

	/** 공급자에게 스키마를 강제 인자로 보내는가 */
	public boolean enforcesSchema() {
		return this.structuredOutput && this.outputSchema != null;
	}

}
