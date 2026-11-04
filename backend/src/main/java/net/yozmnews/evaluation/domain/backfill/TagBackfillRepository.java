package net.yozmnews.evaluation.domain.backfill;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagBackfillRunView;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.domain.threshold.StoreCoverage;

/**
 * 빠진 태그 채움 저장소 — docs/specs/evaluation.md §4.
 * <p>
 * 구현은 {@code adapter/out/persistence} 의 {@code JdbcTagBackfillRepository} 다. 끝난 채움을 지우는 연산은 없다 — 저장 바닥 계산의
 * 입력이다. 점유한 회차의 쓰기는 점유 토큰을 조건으로 한다.
 */
public interface TagBackfillRepository {

	/**
	 * 끝나지 않은 채움이 없으면 채움과 버전별 계획을 넣고 그 ID 를 돌려준다 — 있으면 아무것도 넣지 않고 빈 값. 동시 요청은 앞 요청의 커밋을
	 * 기다린 뒤 판정된다
	 */
	Optional<Long> insertIfNoneOpen(int days, Instant from, Instant to, Instant eligibleAt,
			List<BackfillVersionPlan> versions);

	/** 끝나지 않은 채움을 읽는다 */
	Optional<TagBackfill> findOpen();

	/** 시작할 수 있고 점유가 없거나 만료된 채움을 행 잠금으로 읽는다 — 다른 트랜잭션이 잡은 행은 건너뛴다. 트랜잭션 안에서 부른다 */
	Optional<TagBackfill> lockDue(Instant now);

	/** {@link #lockDue} 로 잠근 채움의 점유를 쓴다 */
	void saveLocked(TagBackfill claimed);

	/**
	 * 토큰이 맞을 때만 커서를 옮기고 버전별 건수를 더하고 점유를 갱신한다.
	 * @return 썼으면 true — 점유가 바뀌었으면 false
	 */
	boolean advance(long id, UUID token, long cursor, Map<TagSetId, BackfillCounts> counts, Instant leaseExpiresAt);

	/** 토큰이 맞을 때만 끝낸다 — 점유를 푼다 */
	boolean finish(long id, UUID token, Instant now);

	/** 토큰이 맞을 때만 점유를 푼다 — 커서는 그대로 */
	boolean release(long id, UUID token);

	/** 채움의 버전별 계획 */
	Map<TagSetId, BackfillVersionPlan> versions(long id);

	/** 채움 하나를 읽는다 */
	Optional<TagBackfillRunView> run(long id);

	/** 채움 목록을 최근 것부터 읽는다 */
	List<TagBackfillRunView> runs();

	/** 그 버전 · 그 태그 종류의 덮음을 읽는다 — 끝났고 그 버전에서 건너뛴 평가가 없는 채움만, 요청 순 */
	List<StoreCoverage> coverages(TagSetId version, MultiTagKind kind);

}
