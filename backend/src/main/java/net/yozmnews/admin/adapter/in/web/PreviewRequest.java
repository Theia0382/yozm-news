package net.yozmnews.admin.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/**
 * 등록 전 운영자 원문과 미리볼 피드 식별자 — docs/specs/admin.md §3
 */
record PreviewRequest(@NotNull String profile, @NotNull String feedKey) {

}
