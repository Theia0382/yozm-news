package net.yozmnews.user.adapter.in.web;

import net.yozmnews.user.application.ModelSelection.ModelSelectionOutcome;
import org.jspecify.annotations.Nullable;

/**
 * 모델 고르기 결과 — 판정 결과 200. docs/specs/user.md §3 · user-R51
 *
 * @param result 저장 · 키 없는 공급자 · 허용 목록에 없는 모델
 * @param model 저장한 뒤의 모델 조회 — 저장했을 때만
 */
record InterestModelSaveResponse(Result result, @Nullable InterestModelResponse model) {

	static InterestModelSaveResponse of(ModelSelectionOutcome outcome) {
		return switch (outcome) {
			case ModelSelectionOutcome.Saved saved ->
				new InterestModelSaveResponse(Result.SAVED, InterestModelResponse.of(saved.model()));
			case ModelSelectionOutcome.ProviderWithoutKey without ->
				new InterestModelSaveResponse(Result.PROVIDER_WITHOUT_KEY, null);
			case ModelSelectionOutcome.ModelNotAllowed notAllowed ->
				new InterestModelSaveResponse(Result.MODEL_NOT_ALLOWED, null);
		};
	}

	/** 고르기 결과의 판별자 */
	enum Result {

		/** 골랐다 */
		SAVED,

		/** 그 공급자에 키가 없다 */
		PROVIDER_WITHOUT_KEY,

		/** 허용 목록에 없는 모델이다 */
		MODEL_NOT_ALLOWED

	}

}
