package net.yozmnews.user.adapter.in.web;

import java.util.Arrays;
import java.util.List;

import net.yozmnews.user.SummaryLength;
import net.yozmnews.user.domain.interest.CandidateVolume;
import net.yozmnews.user.domain.interest.DigestVolume;
import org.jspecify.annotations.Nullable;

/**
 * 다이제스트 분량 조회 — 고른 값과 화면이 그릴 단계의 값 · 범위. docs/specs/user.md §3
 *
 * @param volume 최대 후보 수의 단계
 * @param customCount 직접 입력한 최대 후보 수 — 직접 입력일 때만
 * @param bigSlots 큰 슬롯 수
 * @param summaryLength 요약 길이
 * @param presets 단계마다 최대 후보 수 — 직접 입력을 뺀 단계, 단계 순서
 * @param countRange 직접 입력한 수의 범위
 * @param bigSlotRange 큰 슬롯 수의 범위
 * @param summaryLengths 고를 수 있는 요약 길이 — 단계 순서
 */
record InterestVolumeResponse(CandidateVolume volume, @Nullable Integer customCount, int bigSlots,
		SummaryLength summaryLength, List<VolumePresetResponse> presets, VolumeRangeResponse countRange,
		VolumeRangeResponse bigSlotRange, List<SummaryLength> summaryLengths) {

	static InterestVolumeResponse of(DigestVolume volume) {
		return new InterestVolumeResponse(volume.volume(), volume.customCount(), volume.bigSlots(),
				volume.summaryLength(),
				Arrays.stream(CandidateVolume.values())
					.filter(CandidateVolume::preset)
					.map(preset -> new VolumePresetResponse(preset, preset.count()))
					.toList(),
				new VolumeRangeResponse(DigestVolume.MIN_COUNT, DigestVolume.MAX_COUNT),
				new VolumeRangeResponse(DigestVolume.MIN_BIG_SLOTS, DigestVolume.MAX_BIG_SLOTS),
				List.of(SummaryLength.values()));
	}

	/**
	 * 단계 하나
	 * @param volume 단계
	 * @param count 그 단계의 최대 후보 수
	 */
	record VolumePresetResponse(CandidateVolume volume, int count) {
	}

	/**
	 * 받는 범위 — 경계 포함
	 * @param min 하한
	 * @param max 상한
	 */
	record VolumeRangeResponse(int min, int max) {
	}

}
