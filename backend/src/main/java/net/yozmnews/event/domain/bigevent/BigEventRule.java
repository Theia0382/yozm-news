package net.yozmnews.event.domain.bigevent;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Objects;

import net.yozmnews.event.BigEvent;
import net.yozmnews.event.BigEventCriteria;
import net.yozmnews.event.EventTally;

/**
 * 큰 사건 판정 규칙의 정본 — docs/specs/event.md §4 · event-R15.
 * <p>
 * 기간 안 서로 다른 소스 수가 소스 하한 이상이고, 중요도 평균이 있고 중요도 하한 이상이면 큰 사건이다(경계 포함).
 */
public final class BigEventRule {

	/** 큰 사건의 순서 — 소스 수 내림차순, 중요도 내림차순, 사건 ID 오름차순 */
	public static final Comparator<BigEvent> ORDER = Comparator
		.comparingInt((BigEvent event) -> event.tally().sources())
		.reversed()
		.thenComparing((BigEvent event) -> event.tally().importance(),
				Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder()))
		.thenComparingLong(event -> event.id().value());

	private BigEventRule() {
	}

	/** 집계가 두 기준에 모두 이르는가 — 경계 포함 */
	public static boolean isBig(EventTally tally, BigEventCriteria criteria) {
		Objects.requireNonNull(tally, "tally");
		Objects.requireNonNull(criteria, "criteria");
		BigDecimal importance = tally.importance();
		return tally.sources() >= criteria.minSources() && importance != null
				&& importance.compareTo(criteria.minImportance()) >= 0;
	}

}
