package net.yozmnews.event.domain.event;

import java.util.List;
import java.util.Objects;

import net.yozmnews.event.domain.assignment.StoredCandidate;

/**
 * 재판정할 배정 하나와 그 재료.
 *
 * @param assignment 잠근 배정 행
 * @param stored 그 기사의 저장 후보 — 사라진 사건은 뺀 것
 */
public record RejudgeTarget(EventArticle assignment, List<StoredCandidate> stored) {

	public RejudgeTarget {
		Objects.requireNonNull(assignment, "assignment");
		stored = List.copyOf(stored);
	}

}
