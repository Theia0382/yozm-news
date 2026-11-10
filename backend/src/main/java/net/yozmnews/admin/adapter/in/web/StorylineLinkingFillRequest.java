package net.yozmnews.admin.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/**
 * 스토리라인 잇기 채우기의 기간(일) — 지금부터 그 일수 안에 마지막 기사가 있는 사건을 채운다. docs/specs/admin.md §3
 */
record StorylineLinkingFillRequest(@NotNull Integer days) {

}
