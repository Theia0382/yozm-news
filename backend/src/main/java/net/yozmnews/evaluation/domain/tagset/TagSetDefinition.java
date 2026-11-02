package net.yozmnews.evaluation.domain.tagset;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.domain.threshold.ThresholdValues;

/**
 * 태그 체계 파일 하나 — 내용과 처음 태그 기준 · 저장 기준 — docs/specs/evaluation.md §4 · evaluation-R10
 */
public record TagSetDefinition(TagSetContent content, Map<MultiTagKind, ThresholdValues> initialThresholds) {

	/**
	 * @throws IllegalArgumentException 기준이 있는 태그 종류마다 처음 태그 기준 · 저장 기준이 하나씩 있지 않을 때
	 */
	public TagSetDefinition {
		Objects.requireNonNull(content, "content");
		EnumMap<MultiTagKind, ThresholdValues> copy = new EnumMap<>(MultiTagKind.class);
		copy.putAll(initialThresholds);
		if (copy.size() != MultiTagKind.values().length) {
			throw new IllegalArgumentException("태그 종류마다 처음 태그 기준 · 저장 기준이 있어야 한다: " + copy.keySet());
		}
		initialThresholds = Map.copyOf(copy);
	}

}
