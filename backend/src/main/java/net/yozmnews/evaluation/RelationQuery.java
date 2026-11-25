package net.yozmnews.evaluation;

import java.util.Objects;

/**
 * 관계 판정 요청 — 견줄 두 스토리라인 · docs/specs/evaluation.md §3 · evaluation-R48.
 * <p>
 * 요청에는 first 가 A, second 가 B 로 실린다. 순서는 부르는 쪽이 정하고 이 모듈은 바꾸지 않는다.
 *
 * @param first 앞에 싣는 스토리라인
 * @param second 뒤에 싣는 스토리라인
 */
public record RelationQuery(StorylineDescriptor first, StorylineDescriptor second) {

	/**
	 * @throws IllegalArgumentException 두 스토리라인 ID 가 같을 때
	 */
	public RelationQuery {
		Objects.requireNonNull(first, "first");
		Objects.requireNonNull(second, "second");
		if (first.storylineId() == second.storylineId()) {
			throw new IllegalArgumentException("두 스토리라인 ID 가 같다: " + first.storylineId());
		}
	}

}
