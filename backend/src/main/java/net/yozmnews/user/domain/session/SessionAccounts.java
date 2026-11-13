package net.yozmnews.user.domain.session;

import java.util.Optional;

import net.yozmnews.user.UserId;

/**
 * 세션 체인의 계정 조회 — 구현은 {@code adapter/out/persistence} 의 {@code JdbcSessionAccounts}. docs/specs/user.md §4 · user-R7
 */
public interface SessionAccounts {

	/**
	 * 쓰는({@code ACTIVE}) 계정의 역할과 강제 변경 여부 — 한 번의 조회. 비밀번호 해시는 읽지 않는다(user-R16).
	 * @return 계정이 없거나 쓰지 않으면 비어 있다
	 */
	Optional<SessionAccount> findActive(UserId id);

}
