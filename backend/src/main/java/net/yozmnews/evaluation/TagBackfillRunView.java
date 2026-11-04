package net.yozmnews.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 빠진 태그 채움 하나 — 덮는 범위 · 상태 · 버전별 결과와 그 합 · docs/specs/evaluation.md §3
 *
 * @param from 덮는 평가 시각의 시작 — 포함
 * @param to 덮는 평가 시각의 끝 — 제외, 요청 시각
 * @param requestedAt 요청 시각 — {@code to} 와 같다
 * @param evaluations 요청 때 센 대상 평가 수의 합
 * @param processed 처리한 평가 수의 합 — 건너뛴 평가 포함
 * @param tagsAdded 더한 태그 행 수의 합
 * @param skippedOtherEvaluator 평가기가 달라 건너뛴 평가 수의 합
 * @param skippedUnreadable 원본 응답을 읽을 수 없거나 체계와 어긋나 건너뛴 평가 수의 합
 * @param versions 버전마다 — 버전 번호 순
 */
public record TagBackfillRunView(long id, int days, Instant from, Instant to, Instant requestedAt,
		TagBackfillState state, @Nullable Instant startedAt, @Nullable Instant finishedAt, long evaluations,
		long processed, long tagsAdded, long skippedOtherEvaluator, long skippedUnreadable,
		List<TagBackfillVersionView> versions) {

	public TagBackfillRunView {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		Objects.requireNonNull(requestedAt, "requestedAt");
		Objects.requireNonNull(state, "state");
		versions = List.copyOf(versions);
	}

}
