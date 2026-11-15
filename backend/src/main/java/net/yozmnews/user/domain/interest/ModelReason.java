package net.yozmnews.user.domain.interest;

/**
 * 고른 모델을 쓰는가, 못 쓰면 그 까닭 — 까닭이 여럿이면 앞의 것 하나다 — docs/specs/user.md §5 · user-R51
 */
public enum ModelReason {

	/** 고른 모델을 쓴다 */
	CHOSEN,

	/** 고르지 않았다 */
	NOT_CHOSEN,

	/** 고른 모델이 허용 목록에서 빠졌다 */
	NOT_ALLOWED,

	/** 고른 모델의 공급자에 키가 없다 */
	NO_KEY,

	/** 고른 모델의 공급자의 키가 무효다 */
	KEY_INVALID

}
