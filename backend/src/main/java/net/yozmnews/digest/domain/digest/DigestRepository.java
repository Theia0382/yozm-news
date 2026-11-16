package net.yozmnews.digest.domain.digest;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * 다이제스트 상태 저장소 — docs/specs/digest.md §4
 */
public interface DigestRepository extends CrudRepository<Digest, Long> {

	/** 다이제스트 행을 잠그고 읽는다 — 생성의 시작 · 끝이 쓰기 전에 부른다. 트랜잭션 안에서 부른다 */
	@Query("select * from digests where id = :id for update")
	Optional<Digest> lock(long id);

}
