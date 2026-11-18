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
	KEY_INVALIDATED,

	/** 태그 체계의 활성 전환이 선호를 해제하거나 더했다 — 사용자 · 활성 전환마다 하나 */
	PREFERENCES_ADJUSTED,

	/** 구독한 스토리라인이 지워져 구독에서 뺐다 — 따라가기 한 번마다 하나 */
	STORYLINE_SUBSCRIPTIONS_REMOVED,

	/** 다이제스트가 실패로 끝났다 — 실패한 다이제스트마다 하나 */
	DIGEST_FAILED

}
