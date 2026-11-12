package net.yozmnews.user.adapter.in.web;

import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRole;
import org.jspecify.annotations.Nullable;

/**
 * 로그인 상태 — 판정 결과 200. docs/specs/user.md §3 · user-R20
 *
 * @param result 로그인됨 · 익명
 * @param userId 사용자 ID — 익명이면 null
 * @param email 이메일 — 익명이면 null
 * @param displayName 표시 이름 — 익명이면 null
 * @param role 역할 — 익명이면 null
 */
record MeResponse(MeResult result, @Nullable Long userId, @Nullable String email, @Nullable String displayName,
		@Nullable AccountRole role) {

	static MeResponse signedIn(Account account) {
		return new MeResponse(MeResult.SIGNED_IN, account.id().value(), account.email().value(),
				account.displayName(), account.role());
	}

	static MeResponse anonymous() {
		return new MeResponse(MeResult.ANONYMOUS, null, null, null, null);
	}

	/** 로그인 상태의 판별자 */
	enum MeResult {

		/** 로그인한 사용자 */
		SIGNED_IN,

		/** 로그인하지 않았다 */
		ANONYMOUS

	}

}
