package net.yozmnews.account.domain.session;

import java.util.Objects;

import net.yozmnews.account.domain.account.AccountRole;
import net.yozmnews.platform.UserId;

/**
 * 세션 체인이 요청마다 읽는 쓰는 계정 — 역할과 비밀번호 강제 변경 여부. docs/specs/account.md §4 · account-R7
 *
 * @param id 사용자 ID
 * @param role 역할
 * @param mustChangePassword 첫 로그인에서 비밀번호를 바꿔야 하는가
 */
public record SessionAccount(UserId id, AccountRole role, boolean mustChangePassword) {

	public SessionAccount {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(role, "role");
	}

}
