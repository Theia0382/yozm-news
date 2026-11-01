package net.yozmnews.source.domain.registry;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * 소스 저장소 — docs/specs/source.md §4 · source-R2 · source-R3.
 * <p>
 * 같은 프리셋의 판정은 {@code sources.preset_key} 의 고유 제약이 한다.
 */
public interface SourceRepository extends CrudRepository<Source, Long> {

	/**
	 * 소스 행을 배타 잠금한다 — 삭제가 같은 소스의 운영 변경 · 요청 예약 · 기동 대조와 줄을 선다. 트랜잭션 안에서 부른다.
	 * @return 소스가 없으면 비어 있다
	 */
	@Query("select id from sources where id = :id for update")
	Optional<Long> lockForUpdate(long id);

	/**
	 * 소스 행을 공유 잠금한다 — 운영 변경과는 기다리지 않고 삭제의 배타 잠금과만 줄을 선다. 트랜잭션 안에서 부른다.
	 * @return 소스가 없으면(지워졌으면) 비어 있다
	 */
	@Query("select id from sources where id = :id for key share")
	Optional<Long> lockForKeyShare(long id);

}
