package net.yozmnews.event.domain.assignment;

import java.time.Instant;

import net.yozmnews.event.AssignmentCriteria;

/**
 * 배정 기준 이력 저장소 — docs/specs/event.md §4
 */
public interface CriteriaRepository {

	/**
	 * 기준을 이력에 등록한다 — 같은 값의 행이 있으면 그 ID, 없으면 더한 행의 ID.
	 */
	long register(AssignmentCriteria criteria, Instant now);

}
