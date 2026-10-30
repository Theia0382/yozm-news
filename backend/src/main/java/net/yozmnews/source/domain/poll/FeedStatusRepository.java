package net.yozmnews.source.domain.poll;

import java.time.Instant;
import java.util.List;

import net.yozmnews.source.FeedId;

/** 피드 건강 검사만 쓰는 피드 상태 저장소 — docs/specs/source.md §4 · source-R47 · source-R48 */
public interface FeedStatusRepository {

	/** 판정 대상(소스 · 피드가 모두 켜진 피드)의 행을 잠그고 읽는다 — 호출자의 트랜잭션 안에서 부른다. 피드 ID 순 */
	List<FeedStatusTarget> lockTargets();

	/** {@link #lockTargets} 로 잠근 행의 상태와 바뀐 시각을 쓴다 */
	void saveStatus(FeedId feedId, FeedStatus status, Instant changedAt);

}
