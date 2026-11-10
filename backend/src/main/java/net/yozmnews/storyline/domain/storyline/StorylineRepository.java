package net.yozmnews.storyline.domain.storyline;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.storyline.domain.linking.Representation;

/**
 * 스토리라인 저장소 — 스토리라인 행 · 핵심 말 · 소속 · docs/specs/storyline.md §4.
 */
public interface StorylineRepository {

	/** 이 모듈의 판정 잠금 — 트랜잭션 끝까지 쥔다(storyline-R5). 트랜잭션 안에서 부른다 */
	void lockLinking();

	/** 사건이 든 스토리라인 — ID 순 */
	List<Long> storylinesOf(long eventId);

	/** 스토리라인마다 구성 사건 — 스토리라인 ID 순, 사건 ID 순. 구성 사건이 없는 스토리라인은 빠진다 */
	Map<Long, List<Long>> members(Collection<Long> storylineIds);

	/** 이 가운데 소속이 있는 사건 */
	Set<Long> affiliated(Collection<Long> eventIds);

	/**
	 * 후보 스토리라인(storyline-R9) — 모델 버전이 같고, 시각 범위가 {@code [from, to]} 와 겹치고, 중심의 코사인 유사도가
	 * {@code minCosine} 이상인 것(코사인이 NaN 이면 빠진다). 핵심 말은 기사 수가 많은 순(같으면 말 바이트 순) 상위 {@code keyTerms} 개.
	 * @param vector 판정할 사건 중심
	 * @return 스토리라인 ID 순
	 */
	List<StorylineSnapshot> candidates(String modelVersion, float[] vector, Instant from, Instant to, double minCosine,
			int keyTerms);

	/** 새 스토리라인을 연다 — 표현과 핵심 말을 쓴다 */
	long open(Representation representation, Instant now);

	/** 스토리라인의 표현을 쓴다 — 핵심 말은 통째로 바꾼다 */
	void save(long storylineId, Representation representation, Instant now);

	/** 소속을 더한다 */
	void addMember(long storylineId, long eventId, LinkJudgement judgement);

	/** 소속의 사건을 바꾼다 — 근거를 {@code MERGE} 로 다시 적는다(storyline-R6) */
	void moveMember(long storylineId, long fromEventId, long toEventId, Instant now);

	/** 소속을 지운다 */
	void removeMember(long storylineId, long eventId);

	/** 스토리라인과 그 핵심 말 · 소속을 지운다(storyline-R13) */
	void delete(long storylineId);

}
