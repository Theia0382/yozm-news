package net.yozmnews.event.domain.event;

import java.util.List;
import java.util.Objects;

import net.yozmnews.event.domain.assignment.StoredCandidate;

/**
 * 재판정할 배정 하나와 그 재료.
 *
 * @param assignment 잠근 배정 행
 * @param stored 그 기사의 저장 후보 — 사라진 사건은 빼고, 합쳐진 사건은 남는 사건으로 읽은 것. 같은 사건이 둘이면
 * {@link StoredCandidate#folded} 로 모은다
 */
public record RejudgeTarget(EventArticle assignment, List<StoredCandidate> stored) {

	public RejudgeTarget {
		Objects.requireNonNull(assignment, "assignment");
		stored = StoredCandidate.folded(stored);
	}

}
