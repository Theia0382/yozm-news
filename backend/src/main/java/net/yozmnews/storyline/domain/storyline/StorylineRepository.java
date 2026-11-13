package net.yozmnews.storyline.domain.storyline;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.storyline.domain.linking.Representation;

/**
 * 스토리라인 저장소 — 스토리라인 행 · 핵심 말 · 소속 · 합쳐진 스토리라인의 가리킴 · docs/specs/storyline.md §4.
 * <p>
 * 합쳐진 스토리라인은 소속이 없어 소속으로 읽는 조회({@link #storylinesOf} · {@link #members} · {@link #affiliated})에서 저절로
 * 빠지고, 후보 조회는 조건으로 뺀다(storyline-R18).
 */
public interface StorylineRepository {

	/** 이 모듈의 판정 잠금 — 트랜잭션 끝까지 쥔다(storyline-R5). 트랜잭션 안에서 부른다 */
	void lockLinking();

	/** 사건이 든 합쳐지지 않은 스토리라인 — ID 순 */
	List<Long> storylinesOf(long eventId);

	/** 스토리라인마다 구성 사건 — 스토리라인 ID 순, 사건 ID 순. 구성 사건이 없는 스토리라인(합쳐진 스토리라인 포함)은 빠진다 */
	Map<Long, List<Long>> members(Collection<Long> storylineIds);

	/** 이 가운데 합쳐지지 않은 스토리라인에 소속이 있는 사건 */
	Set<Long> affiliated(Collection<Long> eventIds);

	/**
	 * 후보 스토리라인(storyline-R9) — 합쳐지지 않았고, 모델 버전이 같고, 시각 범위가 {@code [from, to]} 와 겹치고, 중심의 코사인 유사도가
	 * {@code minCosine} 이상인 것(코사인이 NaN 이면 빠진다). 핵심 말은 기사 수가 많은 순(같으면 말 바이트 순) 상위 {@code keyTerms} 개.
	 * @param vector 판정할 사건 중심
	 * @return 스토리라인 ID 순
	 */
	List<StorylineSnapshot> candidates(String modelVersion, float[] vector, Instant from, Instant to, double minCosine,
			int keyTerms);

	/**
	 * 합쳐지지 않은 스토리라인 하나의 표현과 구성 사건 수 — 관계 후보의 판정할 쪽 · 견줄 쪽(storyline-R27). 핵심 말은 {@link #candidates}
	 * 와 같은 순서로 상위 {@code keyTerms} 개.
	 * @return 없거나 합쳐진 스토리라인이면 빈 값
	 */
	Optional<StorylineProfile> profile(long storylineId, int keyTerms);

	/** 스토리라인마다 소속의 판정 근거 — 스토리라인 → 사건 → 근거, ID 순(storyline-R34) */
	Map<Long, Map<Long, LinkJudgement>> judgements(Collection<Long> storylineIds);

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

	/**
	 * 스토리라인 merged 를 survivor 에 합친다(storyline-R18) — merged 의 소속 · 핵심 말을 지우고, merged 와 merged 를 가리키던 행이
	 * survivor 를 가리키게 한다. 합친 시각은 merged 에만 적는다. survivor 의 소속 · 표현은 바꾸지 않는다
	 */
	void merge(long merged, long survivor, Instant now);

	/**
	 * 스토리라인과 그 핵심 말 · 소속, 이 스토리라인을 가리키는 합쳐진 행을 지운다(storyline-R13 · R17 · R18). 그 관계 · 물은 기록 · 관계 대기
	 * 행도 함께 지워진다(storyline-R32)
	 */
	void delete(long storylineId);

}
