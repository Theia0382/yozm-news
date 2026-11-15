package net.yozmnews.credential.domain.key;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * 키 저장소 — docs/specs/credential.md §4. 주인마다 키가 많아야 하나임은 {@code llm_credentials} 의 고유 제약이 지킨다(credential-R11).
 */
public interface LlmKeyRepository extends CrudRepository<StoredLlmKey, Long> {

	/** 주인의 키 — 공급자는 {@code Provider.key()} */
	Optional<StoredLlmKey> findByUserIdAndProvider(long userId, String provider);

	/** 사용자의 키 전부 */
	List<StoredLlmKey> findByUserId(long userId);

	/** 사용자의 키가 하나라도 있는가 */
	boolean existsByUserId(long userId);

	/** 키 행을 잠그고 읽는다 — 상태 전이와 재암호화가 쓰기 전에 부른다. 트랜잭션 안에서 부른다 */
	@Query("select * from llm_credentials where id = :id for update")
	Optional<StoredLlmKey> lockById(long id);

	/** 주인의 키 행을 잠그고 읽는다 — 교체가 지우기 전에 부른다. 트랜잭션 안에서 부른다 */
	@Query("select * from llm_credentials where user_id = :userId and provider = :provider for update")
	Optional<StoredLlmKey> lockByOwner(long userId, String provider);

	/** DEK 를 감싼 KEK 가 활성 버전이 아닌 키의 ID — 작은 ID 부터 {@code limit} 개까지 */
	@Query("select id from llm_credentials where kek_version <> :active order by id limit :limit")
	List<Long> idsNotOnVersion(int active, int limit);

	/** DEK 를 감싼 KEK 가 활성 버전이 아닌 키의 수 */
	@Query("select count(*) from llm_credentials where kek_version <> :active")
	long countNotOnVersion(int active);

	/** KEK 버전마다 그 버전을 가리키는 키의 수 — 버전 순 */
	@Query("select kek_version, count(*) as keys from llm_credentials group by kek_version order by kek_version")
	List<KekVersionUse> versionsInUse();

}
