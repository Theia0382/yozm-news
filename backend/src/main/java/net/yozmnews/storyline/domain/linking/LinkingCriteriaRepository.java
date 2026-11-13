package net.yozmnews.storyline.domain.linking;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * 잇기 기준 이력 저장소 — docs/specs/storyline.md §4 · storyline-R14
 */
public interface LinkingCriteriaRepository {

	/** 기준을 이력에 등록한다 — 같은 값의 행이 있으면 그 ID, 없으면 더한 행의 ID */
	long register(LinkingCriteria criteria, Instant now);

	/** 기준 이력 ID → 값 — 없는 ID 는 빠진다(storyline-R34) */
	Map<Long, LinkingCriteria> findAll(Collection<Long> ids);

}
