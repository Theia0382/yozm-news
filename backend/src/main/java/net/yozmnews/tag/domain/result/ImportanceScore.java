package net.yozmnews.tag.domain.result;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

/**
 * 중요도의 대표값 — 확률 가중 기대값의 정본 · docs/specs/tag.md §5
 */
public final class ImportanceScore {

	private ImportanceScore() {
	}

	/**
	 * 기대값 — Σ(수준 × 확률) ÷ Σ확률. 분포의 합이 1 이 아니어도 정의된다. 수준 순으로 더하고, 결과를 확률이 있는 수준의 최솟값 ~ 최댓값으로
	 * 맞춘다 — 기대값은 그 범위 안이므로 부동소수 오차만 걷어 낸다(예: 수준 5 에만 확률이 있으면 정확히 5).
	 * @return 확률 합이 0 이면 null
	 */
	public static @Nullable Double expected(Map<Integer, Double> distribution) {
		Objects.requireNonNull(distribution, "distribution");
		double weighted = 0;
		double total = 0;
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		for (Map.Entry<Integer, Double> entry : new TreeMap<>(distribution).entrySet()) {
			weighted += entry.getKey() * entry.getValue();
			total += entry.getValue();
			if (entry.getValue() > 0) {
				lowest = Math.min(lowest, entry.getKey());
				highest = Math.max(highest, entry.getKey());
			}
		}
		if (total == 0) {
			return null;
		}
		return Math.clamp(weighted / total, lowest, highest);
	}

}
