package net.yozmnews.storyline.domain.relation;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.yozmnews.storyline.RelationOutcome;
import org.jspecify.annotations.Nullable;

/**
 * 관계 저장소 — 관계 · 물은 기록 · 관계 기준 이력 · docs/specs/storyline.md §4 · storyline-R25 · storyline-R30 · storyline-R32.
 * <p>
 * 자기 자신 · 쌍마다 하나 · 포함의 상위 하나는 DB 제약이 함께 막는다. 순환은 판정이 모듈 잠금 안에서 확인한다.
 */
public interface RelationRepository {

	/** 합쳐지지 않은 스토리라인 사이의 모든 관계 — 종류 · 앞 ID · 뒤 ID 순 */
	List<Relation> all();

	/** 그 스토리라인이 앞이거나 뒤인 관계 — 종류 · 앞 ID · 뒤 ID 순 */
	List<StoredRelation> of(long storylineId);

	/** 그 쌍의 관계를 이것으로 둔다 — 그 쌍에 있던 관계는 지운다 */
	void link(Relation relation, double probability, long judgementId, long criteriaId, Instant now);

	/** 그 쌍의 관계를 지운다 — 방향과 무관, 없으면 아무것도 하지 않는다 */
	void unlink(long a, long b);

	/** 이 스토리라인과 물은 상대 → 물었을 때 작은 쪽의 구성 사건 수 */
	Map<Long, Integer> askedAtSmallerEvents(long storylineId);

	/** 관계 기준 이력 ID → 값 — 없는 ID 는 빠진다(storyline-R34) */
	Map<Long, RelationCriteria> criteria(Collection<Long> ids);

	/** 이 스토리라인이 든 물은 기록 — 상대 ID 순(storyline-R30 · R34) */
	List<StoredAsked> askedOf(long storylineId);

	/**
	 * 물은 기록을 남긴다 — 쌍마다 한 행이고 다시 물으면 덮어쓴다.
	 * @param smallerEvents 물었을 때 작은 쪽의 구성 사건 수
	 * @param judgementId 판정 원본 ID — 원본이 남지 않은 결말(물을 것 없음 · 입력 거부)이면 null
	 */
	void recordAsked(long a, long b, int smallerEvents, @Nullable Long judgementId, long criteriaId,
			RelationOutcome outcome, Instant now);

	/**
	 * 기준을 기준 이력에 등록한다.
	 * @return 그 값의 행 ID — 같은 값의 행이 있으면 그 ID
	 */
	long registerCriteria(RelationCriteria criteria, Instant now);

	/** 계획의 지우기와 옮기기를 한다 — 옮긴 관계의 시각을 {@code now} 로 적는다 */
	void applyMove(RelationMove.Plan plan, Instant now);

	/** 그 스토리라인의 물은 기록을 지운다 */
	void forgetAsked(long storylineId);

}
