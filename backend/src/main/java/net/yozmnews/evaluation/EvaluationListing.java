package net.yozmnews.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 평가 목록 한 번 — 평가 시각 · ID 내림차순 · docs/specs/evaluation.md §3 · evaluation-R33.
 *
 * @param next 맞는 평가가 더 있으면 마지막 행의 커서, 없으면 null
 */
public record EvaluationListing(TagSetId version, Instant from, Instant to, List<EvaluationRow> rows,
		@Nullable EvaluationCursor next) {

	public EvaluationListing {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		rows = List.copyOf(rows);
	}

}
