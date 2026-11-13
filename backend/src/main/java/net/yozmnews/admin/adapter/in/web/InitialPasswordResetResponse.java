package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.user.InitialPasswordResetResult;

/**
 * 첫 로그인 전 계정의 초기 비밀번호 다시 정하기 결과 — docs/specs/admin.md §3 · admin-R1. 없는 계정은 404, 비밀번호 규칙 위반은 400 이다.
 *
 * @param result 다시 정함 · 본인이 이미 바꾼 계정
 */
record InitialPasswordResetResponse(Outcome result) {

	/**
	 * 결과를 응답으로 옮긴다.
	 * @throws org.springframework.web.server.ResponseStatusException 404 — 없는 계정, 400 — 초기 비밀번호 규칙
	 */
	static InitialPasswordResetResponse of(InitialPasswordResetResult result) {
		return switch (result) {
			case InitialPasswordResetResult.Reset reset -> new InitialPasswordResetResponse(Outcome.RESET);
			case InitialPasswordResetResult.NotPending pending -> new InitialPasswordResetResponse(Outcome.NOT_PENDING);
			case InitialPasswordResetResult.NoAccount none -> throw AccountProblem.noAccount();
			case InitialPasswordResetResult.PasswordRejected rejected -> throw AccountProblem.passwordRejected(rejected.problem());
		};
	}

	enum Outcome {

		/** 다시 정했다 — 그 계정의 세션은 모두 끝났다 */
		RESET,

		/** 본인이 이미 비밀번호를 바꾼 계정이라 바꾸지 않았다 */
		NOT_PENDING

	}

}
