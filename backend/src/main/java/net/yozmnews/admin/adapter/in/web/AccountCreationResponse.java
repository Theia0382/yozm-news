package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.user.AccountCreationResult;
import org.jspecify.annotations.Nullable;

/**
 * 운영자 계정 생성의 결과 — docs/specs/admin.md §3 · admin-R1. 형식 · 규칙 위반은 결과가 아니라 400 이다.
 *
 * @param result 만듦 · 이미 있는 이메일
 * @param userId 만든 계정의 사용자 ID — 만들었을 때만
 */
record AccountCreationResponse(Outcome result, @Nullable Long userId) {

	/**
	 * 결과를 응답으로 옮긴다.
	 * @throws org.springframework.web.server.ResponseStatusException 400 — 이메일 · 표시 이름 형식, 초기 비밀번호 규칙
	 */
	static AccountCreationResponse of(AccountCreationResult result) {
		return switch (result) {
			case AccountCreationResult.Created created -> new AccountCreationResponse(Outcome.CREATED,
					created.user().value());
			case AccountCreationResult.EmailTaken taken -> new AccountCreationResponse(Outcome.EMAIL_TAKEN, null);
			case AccountCreationResult.EmailMalformed malformed -> throw AccountProblem.emailMalformed();
			case AccountCreationResult.DisplayNameMalformed malformed -> throw AccountProblem.displayNameMalformed();
			case AccountCreationResult.PasswordRejected rejected -> throw AccountProblem.passwordRejected(rejected.problem());
		};
	}

	enum Outcome {

		/** 만들었다 — 계정 주인은 첫 로그인에서 비밀번호를 바꾼다 */
		CREATED,

		/** 같은 이메일의 계정이 있다 */
		EMAIL_TAKEN

	}

}
