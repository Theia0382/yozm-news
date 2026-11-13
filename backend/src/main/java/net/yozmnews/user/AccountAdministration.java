package net.yozmnews.user;

import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.credential.PasswordPolicy;

/**
 * 계정의 운영 — 운영자가 이메일 · 표시 이름 · 초기 비밀번호로 계정을 만들고, 첫 로그인 전 계정의 초기 비밀번호를 다시 정한다. {@code admin} 이
 * 운영자 요청으로 부른다. docs/specs/user.md §3.
 * <p>
 * 운영자가 만든 계정은 언제나 일반 사용자이고, 첫 로그인에서 비밀번호를 바꿔야 한다. 운영자는 신뢰 주체라 이메일 중복을 그대로 알린다.
 */
public interface AccountAdministration {

	/** 표시 이름의 최대 길이(코드 포인트) — 운영자 응답의 거부 설명이 읽는다. 정본은 계정의 표시 이름 규칙 */
	int DISPLAY_NAME_MAX_CODE_POINTS = DisplayName.MAX_CODE_POINTS;

	/** 비밀번호의 최소 길이(코드 포인트) — 운영자 응답의 거부 설명이 읽는다. 정본은 비밀번호 규칙 */
	int PASSWORD_MIN_CODE_POINTS = PasswordPolicy.MIN_CODE_POINTS;

	/** 비밀번호의 최대 길이(코드 포인트) — 운영자 응답의 거부 설명이 읽는다. 정본은 비밀번호 규칙 */
	int PASSWORD_MAX_CODE_POINTS = PasswordPolicy.MAX_CODE_POINTS;

	/** 계정을 만든다 — 초기 비밀번호는 첫 로그인에서 바꿔야 한다 */
	AccountCreationResult create(String email, String displayName, String initialPassword);

	/**
	 * 첫 로그인 전(비밀번호 강제 변경 대기) 계정의 초기 비밀번호를 다시 정한다 — 그 계정의 세션은 모두 끝난다. 본인이 비밀번호를 바꾼 계정은
	 * 건드리지 않는다.
	 */
	InitialPasswordResetResult resetInitialPassword(String email, String initialPassword);

}
