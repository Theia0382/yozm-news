package net.yozmnews.user.domain.guard;

/**
 * 요청 제한이 무엇으로 세는가 — docs/specs/user.md §4 · user-R38
 */
public enum LimitAxis {

	/** 클라이언트 주소({@link ClientAddress#limitSubject()}) */
	CLIENT_ADDRESS,

	/** 세션 사용자 — 세션을 요구하는 처리기에만 쓴다 */
	SESSION_USER

}
