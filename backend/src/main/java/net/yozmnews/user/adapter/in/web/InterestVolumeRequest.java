package net.yozmnews.user.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import net.yozmnews.user.SummaryLength;
import net.yozmnews.user.domain.interest.CandidateVolume;
import org.jspecify.annotations.Nullable;

/**
 * 다이제스트 분량 저장 — 통째로 바꾼다. 칸이 없거나 단계 이름이 틀리면 본문 형식 400, 직접 입력({@code CUSTOM})인데 직접 입력한 수가 없거나
 * 그 밖의 단계인데 있으면 400 이다. docs/specs/user.md §3
 *
 * @param volume 최대 후보 수의 단계
 * @param customCount 직접 입력한 최대 후보 수 — {@code CUSTOM} 일 때만
 * @param bigSlots 큰 슬롯 수
 * @param summaryLength 요약 길이
 */
record InterestVolumeRequest(@NotNull CandidateVolume volume, @Nullable Integer customCount,
		@NotNull Integer bigSlots, @NotNull SummaryLength summaryLength) {
}
