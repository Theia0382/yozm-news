package net.yozmnews.source.domain.poll;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;

/**
 * 수집 상태 저장소 — docs/specs/source.md §4 · source-R6 · source-R15 · source-R16.
 * <p>
 * 전이는 {@link FeedPollState} 가 계산하고, 저장소는 그 값을 옮긴다.
 */
public interface FeedPollStateRepository {

	/** 피드의 수집 상태 행을 만든다 — 이미 있으면 아무것도 하지 않는다 */
	void insertIfAbsent(FeedPollState initial);

	Optional<FeedPollState> find(FeedId feedId);

	/**
	 * 지금 대상인 피드를 잠근다 — source-R6 · source-R15. 호출자의 트랜잭션 안에서 부르고, 잠근 행은 {@link #saveLocked} 로
	 * 쓴다.
	 * @param limit 최대 행 수 — 다음 수집 시각이 이른 것부터
	 */
	List<FeedPollContext> lockDue(Instant now, int limit);

	/** 소스마다 유효한 점유(다음 수집 시각이 아직 오지 않은 점유) 가운데 마지막 예약 시각을 찾는다 — 없는 소스는 빠진다 */
	Map<SourceId, Instant> lastScheduledBySource(Collection<SourceId> sources, Instant now);

	/** {@link #lockDue} 로 잠근 행을 전이의 결과(점유 · 미룸)로 덮어쓴다 */
	void saveLocked(FeedPollState state);

	/**
	 * 점유 토큰이 일치할 때만 덮어쓴다.
	 * @return 덮어썼으면 true, 토큰이 일치하지 않아 아무것도 바꾸지 않았으면 false
	 */
	boolean replaceIfLeased(UUID token, FeedPollState next);

	/**
	 * 토큰이 일치하는 점유 중인 피드를 찾는다.
	 * @return 그 피드 — 그사이 소스나 피드가 꺼졌으면 비어 있다
	 */
	Optional<FeedPollContext> findLeased(FeedId feedId, UUID token);

}
