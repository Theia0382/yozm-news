package net.yozmnews.evaluation.domain.evaluator;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yozmnews.evaluation.FlowDescriptor;

/**
 * 관계 판정에 싣는 흐름 하나의 서술 — docs/specs/evaluation.md §4 · evaluation-R48.
 *
 * @param happenings 흐름의 사건 수
 * @param firstAt 흐름의 첫 기사 시각
 * @param lastAt 흐름의 마지막 기사 시각
 * @param described 서술 사건 — 넘긴 순서 그대로 싣는다
 */
public record FlowText(int happenings, Instant firstAt, Instant lastAt, List<HappeningText> described) {

	/**
	 * @throws IllegalArgumentException 사건 수가 1 미만이거나, 첫 기사 시각이 마지막 기사 시각보다 늦거나, 서술이 없거나
	 * {@value FlowDescriptor#MAX_DESCRIBED}개를 넘을 때
	 */
	public FlowText {
		if (happenings < 1) {
			throw new IllegalArgumentException("흐름의 사건 수는 1 이상이다: " + happenings);
		}
		Objects.requireNonNull(firstAt, "firstAt");
		Objects.requireNonNull(lastAt, "lastAt");
		if (firstAt.isAfter(lastAt)) {
			throw new IllegalArgumentException("첫 기사 시각이 마지막 기사 시각보다 늦다: " + firstAt + " > " + lastAt);
		}
		described = List.copyOf(described);
		if (described.isEmpty()) {
			throw new IllegalArgumentException("서술 사건이 없다");
		}
		if (described.size() > FlowDescriptor.MAX_DESCRIBED) {
			throw new IllegalArgumentException(
					"서술 사건은 " + FlowDescriptor.MAX_DESCRIBED + "개까지다: " + described.size());
		}
	}

}
