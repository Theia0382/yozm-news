package net.yozmnews.user.adapter.in.web;

import java.util.List;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.user.application.ModelSelection.ModelView;
import net.yozmnews.user.domain.interest.ModelReason;
import net.yozmnews.user.domain.interest.ModelRef;
import org.jspecify.annotations.Nullable;

/**
 * 모델 조회 — 고를 수 있는 모델 · 고른 모델 · 쓸 모델 · 고른 모델을 못 쓴 까닭. docs/specs/user.md §3 · user-R51
 *
 * @param choosable 고를 수 있는 모델(키가 있는 공급자의 허용 모델 — 목록 순)
 * @param chosen 고른 모델 — 고르지 않았으면 null
 * @param effective 쓸 모델 — 없으면 null
 * @param reason 고른 모델을 쓰는가, 못 쓰면 그 까닭
 */
record InterestModelResponse(List<ModelResponse> choosable, @Nullable ModelResponse chosen,
		@Nullable ModelResponse effective, ModelReason reason) {

	static InterestModelResponse of(ModelView view) {
		ModelRef chosen = view.resolution().chosen();
		LlmModel effective = view.resolution().effective();
		return new InterestModelResponse(
				view.choosable().stream().map(model -> new ModelResponse(model.provider(), model.id())).toList(),
				chosen == null ? null : new ModelResponse(chosen.provider(), chosen.modelId()),
				effective == null ? null : new ModelResponse(effective.provider(), effective.id()),
				view.resolution().reason());
	}

	/**
	 * 모델 하나.
	 *
	 * @param provider 공급자
	 * @param id 공급자의 모델 ID
	 */
	record ModelResponse(Provider provider, String id) {
	}

}
