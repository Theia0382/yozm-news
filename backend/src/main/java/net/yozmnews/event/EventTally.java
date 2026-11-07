package net.yozmnews.event;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/**
 * 기간 안 사건 집계 — 큰 사건 판정의 재료 · docs/specs/event.md §3 · event-R15.
 *
 * @param articles 기간 안 기사 수
 * @param sources 기간 안 기사들의 엔트리가 가진 서로 다른 소스 수
 * @param importance 기간 안 기사 가운데 중요도가 있는 기사의 중요도 평균 — 그런 기사가 없으면 null
 */
public record EventTally(int articles, int sources, @Nullable BigDecimal importance) {
}
