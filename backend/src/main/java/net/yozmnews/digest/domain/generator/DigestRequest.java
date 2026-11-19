package net.yozmnews.digest.domain.generator;

import java.util.Objects;

import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.prompt.DigestPrompt;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.platform.UserId;

/**
 * 생성 요청 — docs/specs/digest.md §4
 * <p>
 * 실제 생성기는 쓸 모델 · 추론 강도 · 프롬프트를, 로컬 대체물은 별칭을 매긴 항목을 쓴다.
 *
 * @param userId 다이제스트의 사용자 — 그 사용자의 저장한 키로 부른다
 * @param model 쓸 모델
 * @param effort 생성의 추론 강도
 * @param candidates 별칭을 매긴 항목 · 기사 — 비어 있지 않다
 * @param prompt 지시 · 사용자 구획 · 출력 스키마
 */
public record DigestRequest(UserId userId, LlmModel model, ReasoningEffort effort, CandidateSet candidates,
		DigestPrompt prompt) {

	/**
	 * @throws IllegalArgumentException 후보가 없을 때
	 */
	public DigestRequest {
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(effort, "effort");
		Objects.requireNonNull(candidates, "candidates");
		Objects.requireNonNull(prompt, "prompt");
		if (candidates.isEmpty()) {
			throw new IllegalArgumentException("후보가 없는 생성 요청이다");
		}
	}

}
