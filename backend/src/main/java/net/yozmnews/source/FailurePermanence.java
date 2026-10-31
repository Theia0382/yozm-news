package net.yozmnews.source;

/**
 * 수집 실패의 분류 — docs/specs/source.md §3 · source-R24. 다음 수집 일정과 피드 상태가 쓴다
 */
public enum FailurePermanence {

	/** 시간이 지나면 풀릴 수 있다 */
	TEMPORARY,
	/** 사람(언론사 · 운영자 · 개발팀)이 무언가를 바꾸기 전에는 다시 보내도 같은 결과가 날 것으로 본다 */
	PERMANENT

}
