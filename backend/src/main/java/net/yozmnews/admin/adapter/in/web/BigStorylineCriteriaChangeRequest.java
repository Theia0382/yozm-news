package net.yozmnews.admin.adapter.in.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

/**
 * 큰 판정 기준 변경 요청 — 새 보도 강도 기준 · docs/specs/admin.md §3.
 */
record BigStorylineCriteriaChangeRequest(@NotNull BigDecimal minIntensity) {
}
