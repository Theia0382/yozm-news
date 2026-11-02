package net.yozmnews.evaluation;

/**
 * 두 기준을 정한 행위자의 종류 — docs/specs/evaluation.md §3 · evaluation-R13.
 * <p>
 * 사용자를 가리키지 않는다 — 지금 운영자는 토큰 하나라 사람을 구별하지 않는다.
 */
public enum ChangeActor {

	/** 첫 기동 적재 */
	SYSTEM,

	/** 공개 운영 API 를 거친 운영자 */
	OPERATOR

}
