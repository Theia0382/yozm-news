package net.yozmnews.admin.adapter.in.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

/**
 * 기준 변경 요청 — 한 태그 종류의 새 태그 기준 · 저장 기준 · docs/specs/admin.md §3.
 */
record ThresholdChangeRequest(@NotNull BigDecimal tagThreshold, @NotNull BigDecimal storeThreshold) {
}
