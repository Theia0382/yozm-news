package net.yozmnews.user.domain.interest;

import java.util.Objects;

import net.yozmnews.llm.Provider;

/**
 * 사용자가 고른 모델 — 공급자와 모델 ID — docs/specs/user.md §5 · user-R51
 */
public record ModelRef(Provider provider, String modelId) {

	public ModelRef {
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(modelId, "modelId");
	}

}
