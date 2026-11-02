package net.yozmnews.evaluation.domain.tagset;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * 태그 체계 버전 저장소 — docs/specs/evaluation.md §4 · evaluation-R2 · evaluation-R3
 */
public interface TagSetRepository extends CrudRepository<TagSet, Long> {

	/** 버전이 하나라도 있는가 */
	@Query("select exists (select 1 from tag_sets)")
	boolean anyExists();

	/** 첫 기동 적재의 표 잠금 — 자기 자신과 충돌하는 모드라 적재끼리 줄을 선다. 트랜잭션 안에서 부른다 */
	@Modifying
	@Query("lock table tag_sets in share row exclusive mode")
	void lockForSeeding();

	/** 활성 버전 — 많아야 하나다(DB 제약) */
	Optional<TagSet> findByActiveTrue();

}
