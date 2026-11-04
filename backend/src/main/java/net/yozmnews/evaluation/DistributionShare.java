package net.yozmnews.evaluation;

import java.util.Objects;

/**
 * 분포의 선택지 하나 — 형식은 키 · 이름, 중요도는 수준 · 수준 기준 · docs/specs/evaluation.md §3.
 */
public record DistributionShare(String key, String name, double probability) {

	public DistributionShare {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
	}

}
