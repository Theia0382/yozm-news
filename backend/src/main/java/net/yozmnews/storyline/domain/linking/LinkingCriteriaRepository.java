package net.yozmnews.storyline.domain.linking;

import java.time.Instant;

/**
 * 잇기 기준 이력 저장소 — docs/specs/storyline.md §4 · storyline-R14
 */
public interface LinkingCriteriaRepository {

	/** 기준을 이력에 등록한다 — 같은 값의 행이 있으면 그 ID, 없으면 더한 행의 ID */
	long register(LinkingCriteria criteria, Instant now);

}
