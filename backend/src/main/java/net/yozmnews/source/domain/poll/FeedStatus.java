package net.yozmnews.source.domain.poll;

/** 피드 상태 — docs/specs/source.md §4 · source-R30 · source-R31 */
public enum FeedStatus {

	/** 기준 안에서 수집되고 있다 */
	ACTIVE,

	/** 계속 실패한다 */
	DEGRADED,

	/** 응답은 정상이지만 오래 새 기사가 없다 */
	STALE

}
