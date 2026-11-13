package net.yozmnews.user.domain.account;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

/**
 * 계정 저장소 — docs/specs/user.md §4 · user-R13. 같은 이메일의 판정은 {@code users.email} 의 고유 제약이 한다.
 */
public interface AccountRepository extends CrudRepository<Account, Long> {

	/** 정규화한 이메일의 계정 */
	Optional<Account> findByEmail(String email);

	/** 이 역할 · 상태의 계정이 있는가 — 첫 운영자 판정이 쓰는 운영자 계정을 본다 */
	boolean existsByRoleAndStatus(AccountRole role, AccountStatus status);

}
