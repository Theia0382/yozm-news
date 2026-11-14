package net.yozmnews.llm;

import java.util.Objects;

/**
 * 허용 모델 하나 — docs/specs/llm.md §3 · llm-R2.
 *
 * @param id 공급자의 모델 ID
 * @param inputTokenBudget 한 번의 생성 요청으로 넣는 입력의 상한(토큰)
 * @param structuredOutput 구조화 출력(JSON 스키마 강제)을 지원하는가
 */
public record LlmModel(Provider provider, String id, int inputTokenBudget, boolean structuredOutput) {

	public LlmModel {
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(id, "id");
	}

}
