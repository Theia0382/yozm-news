package net.yozmnews.user.adapter.in.web;

import net.yozmnews.user.application.TagPreferences.TagPreferenceSaveOutcome;
import org.jspecify.annotations.Nullable;

/**
 * 태그 선호 저장 결과 — 판정 결과 200. docs/specs/user.md §3 · user-R46
 *
 * @param result 저장 · 체계 버전이 바뀜
 * @param preferences 저장한 선호(정규화) — 저장했을 때만
 * @param activeTagSetId 지금 활성 버전 ID — 체계 버전이 바뀌었을 때만
 */
record InterestTagsSaveResponse(Result result, @Nullable PreferencesResponse preferences,
		@Nullable Long activeTagSetId) {

	static InterestTagsSaveResponse of(TagPreferenceSaveOutcome outcome) {
		return switch (outcome) {
			case TagPreferenceSaveOutcome.Saved saved ->
				new InterestTagsSaveResponse(Result.SAVED, PreferencesResponse.of(saved.preferences()), null);
			case TagPreferenceSaveOutcome.TagSetChanged changed ->
				new InterestTagsSaveResponse(Result.TAG_SET_CHANGED, null, changed.active().value());
		};
	}

	/** 저장 결과의 판별자 */
	enum Result {

		/** 저장했다 */
		SAVED,

		/** 화면이 본 체계 버전이 지금 활성 버전이 아니다 — 다시 읽는다 */
		TAG_SET_CHANGED

	}

}
