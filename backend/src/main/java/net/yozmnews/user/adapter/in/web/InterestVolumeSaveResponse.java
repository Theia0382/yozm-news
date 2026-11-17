package net.yozmnews.user.adapter.in.web;

import net.yozmnews.user.adapter.in.web.InterestVolumeResponse.Range;
import net.yozmnews.user.application.DigestVolumeSettings.VolumeOutcome;
import org.jspecify.annotations.Nullable;

/**
 * 다이제스트 분량 저장 결과 — 판정 결과 200. docs/specs/user.md §3
 *
 * @param result 저장 · 직접 입력한 수가 범위 밖 · 큰 슬롯 수가 범위 밖
 * @param volume 저장한 분량 — 저장했을 때만
 * @param range 받는 범위 — 범위 밖일 때만(어긋난 칸의 범위)
 */
record InterestVolumeSaveResponse(Result result, @Nullable InterestVolumeResponse volume, @Nullable Range range) {

	static InterestVolumeSaveResponse of(VolumeOutcome outcome) {
		return switch (outcome) {
			case VolumeOutcome.Saved saved ->
				new InterestVolumeSaveResponse(Result.SAVED, InterestVolumeResponse.of(saved.volume()), null);
			case VolumeOutcome.CountOutOfRange count -> new InterestVolumeSaveResponse(Result.COUNT_OUT_OF_RANGE, null,
					new Range(count.min(), count.max()));
			case VolumeOutcome.BigSlotsOutOfRange bigSlots -> new InterestVolumeSaveResponse(
					Result.BIG_SLOTS_OUT_OF_RANGE, null, new Range(bigSlots.min(), bigSlots.max()));
		};
	}

	/** 저장 결과의 판별자 */
	enum Result {

		/** 저장했다 */
		SAVED,

		/** 직접 입력한 수가 범위 밖이다 */
		COUNT_OUT_OF_RANGE,

		/** 큰 슬롯 수가 범위 밖이다 */
		BIG_SLOTS_OUT_OF_RANGE

	}

}
