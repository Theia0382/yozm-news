package net.yozmnews.admin.adapter.in.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

/**
 * 큰 판정 기준 미리보기 요청 — 제안 기준과 기간(시작일 · 종료일 · 시간대) · docs/specs/admin.md §3.
 */
record BigStorylinePreviewRequest(@NotNull Integer minSources, @NotNull BigDecimal minImportance, @NotNull String from,
		@NotNull String to, @NotNull String zone) {
}
