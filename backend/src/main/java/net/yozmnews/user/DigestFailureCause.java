package net.yozmnews.user;

/**
 * 다이제스트 실패의 갈래 — 실패 알림이 사용자에게 보일 할 일을 고른다. 실패 코드를 갈래로 옮기는 일은 다이제스트가 한다. docs/specs/user.md §3
 */
public enum DigestFailureCause {

	/** LLM 키를 확인해야 한다 — 키가 없거나 무효이거나 쓸 모델이 없다 */
	KEY,

	/** 공급자 계정의 권한 · 잔액을 확인해야 한다 */
	PROVIDER_ACCOUNT,

	/** 잠시 뒤 다시 만들어 볼 수 있다 — 공급자의 일시 장애 · 예상 밖의 답, 생성이 끝나지 않았다 */
	TEMPORARY,

	/** 모델의 출력을 다이제스트로 쓰지 못했다 — 다시 만들거나 다른 모델을 고를 수 있다 */
	OUTPUT

}
