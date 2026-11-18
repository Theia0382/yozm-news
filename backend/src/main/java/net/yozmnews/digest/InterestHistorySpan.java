package net.yozmnews.digest;

import java.util.Objects;

/**
 * 관심사 히스토리가 보일 기간 — 단위(주 · 월)와 기간 수 · docs/specs/digest.md §3
 */
public enum InterestHistorySpan {

	WEEKS_13(Unit.WEEK, 13), WEEKS_26(Unit.WEEK, 26), WEEKS_52(Unit.WEEK, 52),
	MONTHS_12(Unit.MONTH, 12), MONTHS_24(Unit.MONTH, 24), MONTHS_36(Unit.MONTH, 36);

	/** 처음 보일 기간 */
	public static final InterestHistorySpan DEFAULT = WEEKS_13;

	private final Unit unit;

	private final int count;

	InterestHistorySpan(Unit unit, int count) {
		this.unit = unit;
		this.count = count;
	}

	/** 기간의 단위 */
	public Unit unit() {
		return this.unit;
	}

	/** 보일 기간 수 */
	public int count() {
		return this.count;
	}

	/** 단위를 바꿨을 때 보일 기간 — 주는 {@link #WEEKS_13}, 월은 {@link #MONTHS_12} */
	public static InterestHistorySpan defaultOf(Unit unit) {
		Objects.requireNonNull(unit, "unit");
		return switch (unit) {
			case WEEK -> WEEKS_13;
			case MONTH -> MONTHS_12;
		};
	}

	/** 기간의 단위 — 주 · 월 */
	public enum Unit {

		WEEK, MONTH

	}

}
