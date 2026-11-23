package net.yozmnews.user.domain.summary;

import java.util.Objects;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.platform.UserId;

/**
 * 정리 호출 하나의 요청. docs/specs/user.md §4
 *
 * @param user 키의 주인
 * @param model 쓸 모델
 * @param input 재료 — 로컬 대체물이 읽는다
 * @param prompt 프롬프트
 */
public record SummaryRequest(UserId user, LlmModel model, SummaryInput input, SummaryPrompt prompt) {

	public SummaryRequest {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(input, "input");
		Objects.requireNonNull(prompt, "prompt");
	}

}
