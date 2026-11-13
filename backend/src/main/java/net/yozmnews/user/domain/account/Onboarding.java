package net.yozmnews.user.domain.account;

/**
 * 계정을 만드는 길 — 닫힌 집합의 정본. 길마다 만드는 계정의 역할, 첫 로그인의 비밀번호 강제 변경, 만들 때 복구 코드를 주는지가 정해진다.
 * docs/specs/user.md §4.
 * <p>
 * 초대 · 운영자 생성은 언제나 {@link AccountRole#USER} 이고, {@link AccountRole#ADMIN} 이 생기는 길은 첫 운영자 하나다. 남이 정한 초기
 * 비밀번호로 만든 계정은 첫 로그인에서 바꾸기 전까지 강제 변경이고, 복구 코드는 그때 준다.
 */
public enum Onboarding {

	/** 공개 가입 — 가입 정책이 열렸을 때 본인이 */
	OPEN_SIGN_UP,

	/** 초대 링크로 가입 — 본인이 */
	INVITATION,

	/** 운영자 계정 생성 — 운영자가 정한 초기 비밀번호 */
	OPERATOR,

	/** 첫 운영자 — 환경 변수의 초기 비밀번호 */
	FIRST_ADMIN;

	/** 만드는 계정의 역할 */
	public AccountRole role() {
		return switch (this) {
			case OPEN_SIGN_UP, INVITATION, OPERATOR -> AccountRole.USER;
			case FIRST_ADMIN -> AccountRole.ADMIN;
		};
	}

	/** 첫 로그인에서 비밀번호를 바꿔야 하는가 — 남이 정한 초기 비밀번호인가 */
	public boolean mustChangePassword() {
		return switch (this) {
			case OPEN_SIGN_UP, INVITATION -> false;
			case OPERATOR, FIRST_ADMIN -> true;
		};
	}

	/** 만들 때 복구 코드를 주는가 — 강제 변경 계정은 비밀번호를 바꾼 직후에 준다 */
	public boolean recoveryCodeAtCreation() {
		return !mustChangePassword();
	}

}
