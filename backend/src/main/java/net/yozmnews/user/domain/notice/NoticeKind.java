package net.yozmnews.user.domain.notice;

/**
 * 알림 종류 — 닫힌 집합의 정본. docs/specs/user.md §4 · user-R21
 * <p>
 * 종류를 더하면 마이그레이션의 {@code user_notices.kind} 검사와 종류마다의 내용 레코드({@link NoticeContent})를 함께 더한다.
 */
public enum NoticeKind {

	/** 운영자 계정이 받는 피드 상태 변화 — 안 읽은 알림 하나에 모은다 */
	FEED_STATUS,

	/** 비밀번호가 바뀌었다 — 바뀔 때마다 하나 */
	PASSWORD_CHANGED,

	/** LLM 키가 무효가 됐다 — 무효가 되는 전이마다 하나 */
	KEY_INVALIDATED

}
