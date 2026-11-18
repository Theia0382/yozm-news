package net.yozmnews.user.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 구독 제안 거절 — 화면이 본 체계 버전 ID 와 주제 중분류 키. 칸이 없으면 본문 형식 400 이다. docs/specs/user.md §3 · user-R64
 *
 * @param tagSetId 화면이 본 체계 버전 ID(제안 조회가 낸 값)
 * @param topic 거절할 주제 중분류 전체 키
 */
record TopicSuggestionDeclineRequest(@NotNull Long tagSetId, @NotBlank String topic) {
}
