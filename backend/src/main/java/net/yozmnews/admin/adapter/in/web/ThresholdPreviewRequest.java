package net.yozmnews.admin.adapter.in.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import net.yozmnews.tag.MultiTagKind;

/**
 * 기준 미리보기 요청 — 태그 종류 하나의 제안 값과 기간(시작일 · 종료일 · 시간대) · docs/specs/admin.md §3.
 *
 * @param from 시작일(yyyy-MM-dd)
 * @param to 종료일(yyyy-MM-dd, 포함)
 * @param zone 날짜를 해석할 시간대(IANA ID)
 */
record ThresholdPreviewRequest(@NotNull MultiTagKind kind, @NotNull BigDecimal tagThreshold,
		@NotNull BigDecimal storeThreshold, @NotNull String from, @NotNull String to, @NotNull String zone) {
}
