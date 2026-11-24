package net.yozmnews.user.adapter.in.web;

import net.yozmnews.user.application.DigestSettings.DigestSettingsOutcome;
import org.jspecify.annotations.Nullable;

/**
 * 일정 · 리포트 저장 결과 — 판정 결과 200. docs/specs/user.md §3 · user-R50
 *
 * @param result 저장 · 받지 않는 시간대
 * @param settings 저장한 설정 — 저장했을 때만
 */
record InterestSettingsSaveResponse(Result result, @Nullable InterestSettingsResponse settings) {

	static InterestSettingsSaveResponse of(DigestSettingsOutcome outcome) {
		return switch (outcome) {
			case DigestSettingsOutcome.Saved saved ->
				new InterestSettingsSaveResponse(Result.SAVED, InterestSettingsResponse.of(saved.settings()));
			case DigestSettingsOutcome.UnknownTimezone unknown ->
				new InterestSettingsSaveResponse(Result.UNKNOWN_TIMEZONE, null);
		};
	}

	/** 저장 결과의 판별자 */
	enum Result {

		/** 저장했다 */
		SAVED,

		/** 받지 않는 시간대다 */
		UNKNOWN_TIMEZONE

	}

}
