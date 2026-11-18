package net.yozmnews.storyline.domain.storyline;

import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 합쳐진 스토리라인의 행 — docs/specs/storyline.md §4 · storyline-R18.
 *
 * @param id 합쳐진 스토리라인 ID
 * @param mergedAt 합친 시각
 * @param basis 조각 합치기면 그 근거 — 정돈의 합치기면 null
 */
public record MergedRow(long id, Instant mergedAt, @Nullable MergeBasis basis) {

	public MergedRow {
		Objects.requireNonNull(mergedAt, "mergedAt");
	}

}
