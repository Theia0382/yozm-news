package net.yozmnews.llm;

/**
 * 공급자가 답하지 못한 까닭의 종류 — docs/specs/llm.md §3 · llm-R8.
 * <p>
 * 검증과 생성 호출이 함께 쓴다. 종류마다 무엇을 할지는 부르는 쪽이 정한다 — 가르는 쪽은 {@code default} 없는 {@code switch} 로 쓴다.
 */
public enum LlmFailureKind {

	/** 키가 무효다 — 틀린 꼴 · 폐기 · 만료 */
	INVALID_KEY,
	/** 키는 맞지만 그 자원을 쓸 권한이 없다 */
	PERMISSION_DENIED,
	/** 잔액 · 지출 한도가 소진됐다 — 사용자가 결제 · 한도를 고쳐야 풀린다 */
	QUOTA_EXHAUSTED,
	/** 시간이 지나면 풀릴 수 있다 — 연결 · 마감 · 요청 제한 · 서버 오류 · 과부하 */
	TRANSIENT,
	/** 예상 밖의 답이다 — 그 밖의 상태 코드 · 읽을 수 없는 응답 · 상한을 넘은 본문 */
	UNEXPECTED

}
