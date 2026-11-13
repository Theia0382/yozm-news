package net.yozmnews.user.domain.throttle;

/**
 * 실패 기록의 종류 — 같은 일정({@link LoginThrottle})으로 따로 센다. docs/specs/user.md §4 · user-R19
 * <p>
 * 비밀번호를 대조하는 시도(로그인 · 현재 비밀번호 확인)는 {@link #SIGN_IN} 하나를 함께 쓴다 — 같은 비밀의 추측 예산이 하나다. 복구 코드 대조는
 * {@link #RECOVERY} 로 따로 세어, 로그인을 거듭 실패한 사람이 복구에서 기다리지 않는다.
 */
public enum ThrottledAttempt {

	/** 비밀번호 대조 — 로그인과 현재 비밀번호 확인 */
	SIGN_IN,

	/** 복구 코드 대조 */
	RECOVERY

}
