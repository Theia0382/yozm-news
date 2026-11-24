package net.yozmnews.account.domain.account;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * 계정 저장소 — docs/specs/user.md §4 · user-R13. 같은 이메일의 판정은 {@code users.email} 의 고유 제약이 한다.
 */
public interface AccountRepository extends CrudRepository<Account, Long> {

	/** 정규화한 이메일의 계정 */
	Optional<Account> findByEmail(String email);

	/** 이 역할 · 상태의 계정이 있는가 — 첫 운영자 판정이 쓰는 운영자 계정을 본다 */
	boolean existsByRoleAndStatus(AccountRole role, AccountStatus status);

	/** 이 역할 · 상태의 계정 ID — ID 순 */
	@Query("select id from users where role = :role and status = :status order by id")
	List<Long> idsByRoleAndStatus(AccountRole role, AccountStatus status);

	/** 계정 ID 가 {@code after} 보다 큰 계정 ID — ID 순으로 {@code limit} 개까지 */
	@Query("select id from users where id > :after order by id limit :limit")
	List<Long> idsAfter(long after, int limit);

	/** 계정 ID 가 {@code after} 보다 큰 이 상태의 계정 ID — ID 순으로 {@code limit} 개까지 */
	@Query("select id from users where id > :after and status = :status order by id limit :limit")
	List<Long> idsAfterWithStatus(long after, AccountStatus status, int limit);

}
