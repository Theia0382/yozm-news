package net.yozmnews.admin.adapter.in.web;

import java.time.Duration;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * 측정용 시계를 넘기는 요청 본문 — 넘길 길이(ISO-8601 기간, 예: {@code PT24H})와 도착 시각 가운데 하나. docs/specs/admin.md §3
 */
record AdvanceRequest(@Schema(type = "string", format = "duration", example = "PT24H") @Nullable Duration by,
		@Nullable Instant to) {

}
