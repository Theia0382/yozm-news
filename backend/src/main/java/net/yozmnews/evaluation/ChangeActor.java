package net.yozmnews.evaluation;

/**
 * 운영 설정을 정한 행위자의 종류 — 태그 기준 · 저장 기준(evaluation-R13)과 사건 묶기의 큰 사건 기준(event-R21)이 이력에 남긴다 ·
 * docs/specs/evaluation.md §3.
 * <p>
 * 사용자를 가리키지 않는다 — 지금 운영자는 토큰 하나라 사람을 구별하지 않는다.
 */
public enum ChangeActor {

	/** 첫 기동 적재 */
	SYSTEM,

	/** 공개 운영 API 를 거친 운영자 */
	OPERATOR

}
