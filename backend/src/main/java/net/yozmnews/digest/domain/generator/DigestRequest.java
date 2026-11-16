package net.yozmnews.digest.domain.generator;

import java.util.Objects;

import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.platform.UserId;

/**
 * 생성 요청 — docs/specs/digest.md §4
 */
public record DigestRequest(UserId userId, LlmModel model, CandidateSet candidates) {

	public DigestRequest {
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(candidates, "candidates");
		if (candidates.isEmpty()) {
			throw new IllegalArgumentException("후보가 없는 생성 요청이다");
		}
	}

}
