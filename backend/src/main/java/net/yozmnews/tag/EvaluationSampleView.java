package net.yozmnews.tag;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 표본 — 씨앗으로 고른 평가 · docs/specs/tag.md §3 · tag-R34.
 *
 * @param population 표본을 고른 평가의 수(버전 · 기간)
 * @param rows 고른 평가 — 해시 오름차순
 */
public record EvaluationSampleView(TagSetId version, Instant from, Instant to, long seed, long population,
		List<EvaluationRow> rows) {

	public EvaluationSampleView {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		rows = List.copyOf(rows);
	}

}
