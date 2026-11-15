package net.yozmnews.platform;

import java.util.Objects;

/**
 * 요청 제한 · 봇 차단으로 지키는 동작 — 처리기 표시({@link RequestLimited} · {@link HumanChecked})가 가리키는 이름이다.
 * docs/specs/platform.md §3 · platform-R25
 * <p>
 * 동작마다 한도는 그 표시를 읽는 쪽(보안 구성)이 정한다. {@link #actionName()} 은 봇 차단 위젯의 동작 이름이고 화면과의 계약이다 — 영문 소문자 ·
 * 숫자 · {@code -} 로 32자 이하다.
 */
public enum GuardedAction {

	/** 로그인 */
	SIGN_IN("sign-in"),

	/** 복구 코드로 비밀번호 재설정 */
	ACCOUNT_RECOVERY("account-recovery"),

	/** 초대 링크 확인 */
	INVITATION_CHECK("invitation-check"),

	/** 초대 가입 */
	INVITATION_ACCEPTANCE("invitation-acceptance"),

	/** 공개 가입 */
	SIGN_UP("sign-up"),

	/** LLM 키 등록 · 다시 검증 — 둘이 한도를 함께 쓴다 */
	LLM_KEY_CHECK("llm-key-check");

	private final String actionName;

	GuardedAction(String actionName) {
		this.actionName = Objects.requireNonNull(actionName, "actionName");
	}

	/** 봇 차단 위젯의 동작 이름 — 화면과의 계약 */
	public String actionName() {
		return this.actionName;
	}

}
