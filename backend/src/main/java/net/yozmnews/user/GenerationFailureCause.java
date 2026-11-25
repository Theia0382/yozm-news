package net.yozmnews.user;

/**
 * 사용자 키로 하는 생성(다이제스트 · 변화 리포트)이 실패한 갈래 — 실패 알림과 화면이 사용자에게 보일 할 일을 고른다. 실패 코드를 갈래로 옮기는
 * 일은 생성한 모듈이 한다. docs/specs/user.md §3
 */
public enum GenerationFailureCause {

	/** LLM 키를 확인해야 한다 — 키가 없거나 무효이거나 쓸 모델이 없다 */
	KEY,

	/** 공급자 계정의 권한 · 잔액을 확인해야 한다 */
	PROVIDER_ACCOUNT,

	/** 잠시 뒤 다시 만들어 볼 수 있다 — 공급자의 일시 장애 · 예상 밖의 답, 생성이 끝나지 않았다 */
	TEMPORARY,

	/** 모델의 출력을 쓰지 못했다 — 다시 만들거나 다른 모델을 고를 수 있다 */
	OUTPUT

}
