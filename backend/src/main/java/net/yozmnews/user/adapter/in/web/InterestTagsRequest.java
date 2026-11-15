package net.yozmnews.user.adapter.in.web;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/**
 * 태그 선호 저장 — 화면이 본 체계 버전 ID 와 갈래 통째. 칸이 없으면 본문 형식 400 이다. docs/specs/user.md §3 · user-R46
 *
 * @param tagSetId 화면이 본 체계 버전 ID
 * @param topics 구독할 주제(대분류 키 · 중분류 전체 키)
 * @param excludedRegions 제외할 지역
 * @param excludedFormats 제외할 형식
 */
record InterestTagsRequest(@NotNull Long tagSetId, @NotNull List<@NotNull String> topics,
		@NotNull List<@NotNull String> excludedRegions, @NotNull List<@NotNull String> excludedFormats) {
}
