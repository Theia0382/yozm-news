package net.yozmnews.event.domain.assignment;

import java.time.Instant;
import java.util.OptionalLong;

import net.yozmnews.event.AssignmentCriteria;

/**
 * 배정 기준 이력 저장소 — docs/specs/event.md §4
 */
public interface CriteriaRepository {

	/**
	 * 기준을 이력에 등록한다 — 같은 값의 행이 있으면 그 ID, 없으면 더한 행의 ID.
	 */
	long register(AssignmentCriteria criteria, Instant now);

	/**
	 * 같은 값의 기준 행의 ID — 등록하지 않는다(읽기 전용 조회가 쓴다).
	 * @return 아직 등록되지 않았으면 빈 값
	 */
	OptionalLong find(AssignmentCriteria criteria);

}
