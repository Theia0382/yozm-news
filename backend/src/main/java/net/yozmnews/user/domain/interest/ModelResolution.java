package net.yozmnews.user.domain.interest;

import java.util.Objects;

import net.yozmnews.llm.LlmModel;
import org.jspecify.annotations.Nullable;

/**
 * 쓸 모델의 판정 결과 — docs/specs/user.md §5 · user-R51
 * @param chosen 고른 모델 — 고르지 않았으면 null
 * @param effective 쓸 모델 — 없으면 null
 * @param reason 고른 모델을 쓰는가, 못 쓰면 그 까닭
 */
public record ModelResolution(@Nullable ModelRef chosen, @Nullable LlmModel effective, ModelReason reason) {

	public ModelResolution {
		Objects.requireNonNull(reason, "reason");
	}

}
