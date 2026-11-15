package net.yozmnews.user.domain.preference;

/**
 * 따라가기가 사용자의 선호를 바꾼 까닭 — 선호 조정 알림에 싣는다 — docs/specs/user.md §5 · user-R47 · user-R48
 */
public enum AdjustmentKind {

	/** 계보 없이 지워져 해제했다 */
	RELEASED_REMOVED,

	/** 병합의 원천 가운데 일부만 제외해 그 제외를 해제했다 */
	RELEASED_PARTIAL_MERGE,

	/** 구독한 대분류에서 다른 대분류로 옮겨 간 중분류를 구독으로 더했다 */
	ADDED_MOVED_SUBTOPIC,

	/** 새로 생긴 기본 제외 형식을 제외로 더했다 */
	ADDED_DEFAULT_EXCLUDED

}
