package net.yozmnews.evaluation.domain.result;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

/**
 * 중요도의 대표값 — 확률 가중 기대값의 정본 · docs/specs/evaluation.md §5
 */
public final class ImportanceScore {

	private ImportanceScore() {
	}

	/**
	 * 기대값 — Σ(수준 × 확률) ÷ Σ확률. 분포의 합이 1 이 아니어도 정의된다. 수준 순으로 더한다.
	 * @return 확률 합이 0 이면 null
	 */
	public static @Nullable Double expected(Map<Integer, Double> distribution) {
		Objects.requireNonNull(distribution, "distribution");
		double weighted = 0;
		double total = 0;
		for (Map.Entry<Integer, Double> entry : new TreeMap<>(distribution).entrySet()) {
			weighted += entry.getKey() * entry.getValue();
			total += entry.getValue();
		}
		return (total == 0) ? null : weighted / total;
	}

}
