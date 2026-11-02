package net.yozmnews.evaluation.domain.result;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 확률과 기준의 비교 — 저장 기준 거르기와 조회 시점 판정이 함께 쓰는 정본 · docs/specs/evaluation.md §5
 */
public final class Probabilities {

	private Probabilities() {
	}

	/** 확률이 기준 이상인가 — 확률의 십진 표현(소수 셋째 자리 기준과 같은 눈금)으로 비교한다. 경계는 포함한다 */
	public static boolean atLeast(double probability, BigDecimal threshold) {
		Objects.requireNonNull(threshold, "threshold");
		return BigDecimal.valueOf(probability).compareTo(threshold) >= 0;
	}

}
