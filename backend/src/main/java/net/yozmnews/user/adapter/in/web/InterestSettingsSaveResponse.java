package net.yozmnews.user.adapter.in.web;

import net.yozmnews.user.application.DigestSettings.DigestSettingsOutcome;
import org.jspecify.annotations.Nullable;

/**
 * 일정 · 관심사 · 리포트 저장 결과 — 판정 결과 200. docs/specs/user.md §3 · user-R50
 *
 * @param result 저장 · 받지 않는 시간대 · 관심사가 김
 * @param settings 저장한 설정 — 저장했을 때만
 * @param interestTextMax 관심사의 코드 포인트 상한 — 관심사가 길 때만
 */
record InterestSettingsSaveResponse(Result result, @Nullable InterestSettingsResponse settings,
		@Nullable Integer interestTextMax) {

	static InterestSettingsSaveResponse of(DigestSettingsOutcome outcome) {
		return switch (outcome) {
			case DigestSettingsOutcome.Saved saved ->
				new InterestSettingsSaveResponse(Result.SAVED, InterestSettingsResponse.of(saved.settings()), null);
			case DigestSettingsOutcome.UnknownTimezone unknown ->
				new InterestSettingsSaveResponse(Result.UNKNOWN_TIMEZONE, null, null);
			case DigestSettingsOutcome.InterestTextTooLong tooLong ->
				new InterestSettingsSaveResponse(Result.INTEREST_TEXT_TOO_LONG, null, tooLong.max());
		};
	}

	/** 저장 결과의 판별자 */
	enum Result {

		/** 저장했다 */
		SAVED,

		/** 받지 않는 시간대다 */
		UNKNOWN_TIMEZONE,

		/** 관심사가 상한을 넘는다 */
		INTEREST_TEXT_TOO_LONG

	}

}
