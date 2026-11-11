package net.yozmnews.tag;

import java.util.Objects;

/**
 * 원문의 태그 기준 · 저장 기준({@code proposed})이 기준 버전의 지금 값({@code current})과 크기가 다른 태그 종류 하나 —
 * docs/specs/tag.md §3 · tag-R24
 */
public record ThresholdDifference(TagThresholds current, TagThresholds proposed) {

	/**
	 * @throws IllegalArgumentException {@code current} · {@code proposed} 의 태그 종류가 다를 때
	 */
	public ThresholdDifference {
		Objects.requireNonNull(current, "current");
		Objects.requireNonNull(proposed, "proposed");
		if (current.kind() != proposed.kind()) {
			throw new IllegalArgumentException("태그 종류가 다르다: " + current.kind() + " · " + proposed.kind());
		}
	}

}
