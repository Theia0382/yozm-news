package net.yozmnews.reader.adapter.in.web;

import org.jspecify.annotations.Nullable;

/**
 * 주제 하나의 기간 하나의 관심 — docs/specs/reader.md §5
 *
 * @param level 관심(−1 ~ 1) — 그 기간에 노출 · 구독 신호가 없으면 null
 */
record InterestLevelResponse(@Nullable Double level) {
}
