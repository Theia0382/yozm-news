package net.yozmnews.storyline.domain.linking;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * 물은 기록 저장소 — 붙이기 띠(사건, 후보 스토리라인)와 짝 띠(사건, 이웃 사건) · docs/specs/storyline.md §4 · storyline-R36.
 * <p>
 * 판정의 확률은 이 기록이 정본이다. 쓰기 트랜잭션 안에서 부른다. 방향이 있다 — 판정할 사건이 앞이다.
 */
public interface LinkingAskedRepository {

	/** 판정할 사건과 주어진 후보 스토리라인의 기록 — 스토리라인 ID → 기록 */
	Map<Long, AskedRecord> ofLink(long eventId, Collection<Long> storylineIds);

	/** 판정할 사건과 주어진 이웃 사건의 기록 — 이웃 사건 ID → 기록 */
	Map<Long, AskedRecord> ofPair(long eventId, Collection<Long> partnerIds);

	/** 붙이기 띠의 기록을 쓴다 — 스토리라인 ID → 기록, 있으면 덮어쓴다 */
	void recordLink(long eventId, Map<Long, AskedRecord> records, Instant now);

	/** 짝 띠의 기록을 쓴다 — 이웃 사건 ID → 기록, 있으면 덮어쓴다 */
	void recordPair(long eventId, Map<Long, AskedRecord> records, Instant now);

	/** 그 스토리라인의 기록을 지운다 — 합쳐질 스토리라인. 지운 스토리라인은 FK 로 함께 지워진다 */
	void forgetStoryline(long storylineId);

	/** 그 스토리라인에 물은 붙이기 기록 가운데 이 사건들의 것을 지운다 — 그 사건들이 그 스토리라인의 소속이 됐다 */
	void forgetLinks(long storylineId, Collection<Long> eventIds);

	/** 그 사건이 판정할 쪽이거나 이웃 쪽인 기록을 두 표 모두에서 지운다 — 지워졌거나 합쳐진 사건 */
	void forgetEvent(long eventId);

}
