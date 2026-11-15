package net.yozmnews.user.domain.session;

/**
 * 요청의 처리기와 그 표시 — 세션 접근 판정의 입력(user-R5).
 *
 * @param present 요청을 받을 처리기가 있는가
 * @param isPublic 공개 표시가 있는가
 * @param beforePasswordChange 비밀번호 변경 전 허용 표시가 있는가
 * @param withoutLlmKey 키 없이 허용 표시가 있는가
 */
public record HandlerMarks(boolean present, boolean isPublic, boolean beforePasswordChange, boolean withoutLlmKey) {

	/** 처리기가 없는 요청 */
	public static final HandlerMarks NO_HANDLER = new HandlerMarks(false, false, false, false);

}
