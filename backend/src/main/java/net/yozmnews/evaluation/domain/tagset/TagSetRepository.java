package net.yozmnews.evaluation.domain.tagset;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * 태그 체계 버전 저장소 — docs/specs/evaluation.md §4 · evaluation-R2 · evaluation-R3 · evaluation-R16.
 * <p>
 * 표 잠금은 두 모드다 — 적재 · 활성화는 {@link #lockForChange()}, 회차의 결과 쓰기 · 태그 기준 · 저장 기준 변경은
 * {@link #lockAgainstChange()}. 뒤 모드끼리는 서로 막지 않고, 앞 모드와는 서로 막는다. 행 잠금({@code FOR SHARE})을 쓰지 않는 것은,
 * READ COMMITTED 에서 전환을 기다린 문장이 다시 평가할 때 끈 옛 행만 보고 새 활성 행은 보지 못하기 때문이다.
 */
public interface TagSetRepository extends CrudRepository<TagSet, Long> {

	/** 버전이 하나라도 있는가 */
	@Query("select exists (select 1 from tag_sets)")
	boolean anyExists();

	/** 첫 기동 적재 · 활성화의 표 잠금 — 자기 자신 · {@link #lockAgainstChange()} 와 충돌하는 모드. 트랜잭션 안에서 부른다 */
	@Modifying
	@Query("lock table tag_sets in share row exclusive mode")
	void lockForChange();

	/** 회차의 결과 쓰기 · 태그 기준 · 저장 기준 변경의 표 잠금 — 서로는 막지 않고 {@link #lockForChange()} 와 충돌한다 */
	@Modifying
	@Query("lock table tag_sets in share mode")
	void lockAgainstChange();

	/** 활성 버전 — 많아야 하나다(DB 제약) */
	Optional<TagSet> findByActiveTrue();

	/** 그 버전이 활성인가 */
	@Query("select active from tag_sets where id = :id")
	boolean isActive(@Param("id") long id);

	/**
	 * 활성인 그 버전을 끈다 — 조건부 갱신이라 aggregate 를 다시 저장하지 않는다.
	 * @return 껐으면 참 — 이미 꺼졌거나 없으면 거짓
	 */
	@Modifying
	@Query("update tag_sets set active = false where id = :id and active")
	boolean deactivate(@Param("id") long id);

	/** 버전 목록 — 번호가 큰 것부터 */
	List<TagSet> findAllByOrderByNumberDesc();

	/** 그 번호부터의 버전 — 번호 순 */
	List<TagSet> findByNumberGreaterThanEqualOrderByNumber(int number);

}
