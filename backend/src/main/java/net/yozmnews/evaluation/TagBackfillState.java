package net.yozmnews.evaluation;

/**
 * 빠진 태그 채움의 상태 — docs/specs/evaluation.md §3
 */
public enum TagBackfillState {

	/** 기록했고 아직 시작하지 않았다 */
	WAITING,
	/** 시작했고 끝나지 않았다 */
	RUNNING,
	/** 끝났다 — 덮은 범위가 저장 바닥 계산에 든다(그 버전에서 건너뛴 평가가 없을 때) */
	DONE

}
