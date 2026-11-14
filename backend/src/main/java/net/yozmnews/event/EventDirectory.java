package net.yozmnews.event;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import org.jspecify.annotations.Nullable;

/**
 * 다른 모듈이 사건을 읽는 조회 — 사건 · 사건의 기사 · 기사의 사건 · 대표 기사 · 사건 목록 · 기간 안 사건 · 사건 묶음의 기간
 * 집계와 스토리라인 잇기의 재료(사건 표현 · 이웃 사건 · 말 수 · 채우기 대상) · docs/specs/event.md §3 · event-R14 · event-R15 · event-R20 ·
 * event-R24.
 * <p>
 * 사용자 정보를 받지 않는다. 트랜잭션을 열지 않는다 — 각 질의는 그 순간의 값이고, 기간 집계의 재료(소스 · 중요도)는 다른 모듈의 공개 조회로
 * 따로 읽는다.
 */
public interface EventDirectory {

	/** 사건 목록 한 쪽의 상한 — 정본. 측정 전 목표 */
	int MAX_PAGE_SIZE = 100;

	/** 표현 · 이웃 조회의 핵심 말 수 상한 — 정본 */
	int MAX_KEY_TERMS = 100;

	/** 이웃 조회 한 번의 상한 — 정본 */
	int MAX_NEIGHBORS = 100;

	/** 말 수 조회 한 번에 묻는 말의 상한 — 정본. {@link #MAX_KEY_TERMS}(표현 · 이웃이 내는 핵심 말 수)와 다른 값이다 */
	int MAX_TERMS = 100;

	/**
	 * 사건 하나 — 합쳐진 사건(event-R13)이면 남는 사건을 낸다.
	 * @return 없는 사건이면 빈 값
	 */
	Optional<EventView> find(EventId id);

	/**
	 * 사건들 — 합쳐진 사건 · 없는 사건은 빠진다(event-R20).
	 * @return 사건 ID → 사건 — 빈 입력이면 빈 맵
	 */
	Map<EventId, EventView> views(Collection<EventId> ids);

	/**
	 * 사건의 기사(배정) 전부 — 발행 시각 · 기사 ID 순.
	 * @return 없는 사건이면 빈 목록
	 */
	List<EventArticleView> articles(EventId id);

	/**
	 * 기사들의 사건.
	 * @return 기사 ID → 사건 ID — 배정이 없는 기사는 빠진다. 빈 입력이면 빈 맵
	 */
	Map<ArticleId, EventId> eventsOf(Collection<ArticleId> articleIds);

	/**
	 * 사건마다 주어진 기사 가운데 대표 기사(event-R14) — 사건 모델 버전의 표현이 있는 기사를 중심에 가까운 순으로 먼저, 표현이 없는 기사는
	 * 그 뒤에 발행 시각 · 기사 ID 순으로 두고 첫째를 고른다.
	 * @return 사건 ID → 대표 기사 — 그 사건의 기사가 아닌 것은 무시하고, 남는 기사가 없는 사건은 빠진다
	 */
	Map<EventId, ArticleId> representatives(Map<EventId, ? extends Collection<ArticleId>> among);

	/**
	 * 사건마다 사건 기사 전체에서 고른 대표 기사(event-R14) — 닫을 때 남긴 대표가 있으면 그것이다. {@link #list} 의 대표와 같은 정본이다.
	 * @return 사건 ID → 대표 기사 — 배정이 없는 사건(합쳐진 사건 포함) · 없는 사건은 빠진다. 빈 입력이면 빈 맵
	 */
	Map<EventId, ArticleId> representativesOfAll(Collection<EventId> events);

	/**
	 * 기간 안 기사가 있는 사건과 그 기간 안 기사 수 — 사건 ID 순(event-R20). 합쳐진 사건은 배정이 없어 나오지 않는다.
	 */
	Map<EventId, Integer> articleCountsIn(EventPeriod period);

	/**
	 * 사건 묶음마다 기간 집계 — 묶음의 사건들의 기간 안 기사를 한데 모아 센다(event-R15). 한 묶음 안에서 같은 사건은 한 번만 세고, 한 사건이
	 * 여러 묶음에 들면 묶음마다 센다. 무엇을 한 묶음으로 셀지와 집계로 무엇을 판정할지는 부르는 쪽이 정한다.
	 * @return 묶음 키 → 집계 — 입력의 모든 키가 있다(기간 안 기사가 없는 묶음은 기사 0 · 소스 0 · 중요도 없음). 빈 입력이면 빈 맵
	 */
	<K> Map<K, EventTally> tallies(Map<K, ? extends Collection<EventId>> groups, EventPeriod period);

	/**
	 * 기간 전체 집계 — 조회 기간 안 기사가 있는 모든 사건({@link #articleCountsIn} 과 같은 범위)의 기간 안 기사를 한데 모아
	 * 센다(event-R15).
	 * @return 기간 안 기사가 없으면 기사 0 · 소스 0 · 중요도 없음 · 중요도 합 0
	 */
	EventTally total(EventPeriod period);

	/**
	 * 기간 안 기사가 있는 사건 — 마지막 기사 시각 내림차순, 사건 ID 내림차순. 집계는 이 기간, 대표 기사는 사건 기사 전체에서.
	 * 쪽 사이에 마지막 기사 시각이 바뀐 사건은 건너뛰거나 다시 나올 수 있다.
	 * @param after 앞 쪽의 {@link EventPage#next()} — 첫 쪽이면 null
	 * @throws IllegalArgumentException {@code size} 가 1 ~ {@link #MAX_PAGE_SIZE} 밖일 때
	 */
	EventPage list(EventPeriod period, @Nullable EventCursor after, int size);

	/**
	 * 사건들의 표현 — 핵심 말은 기사 수가 많은 순(같으면 말 바이트 순) 상위 {@code keyTerms} 개(event-R24).
	 * @return 사건 ID → 표현 — 합쳐진 사건 · 없는 사건은 빠진다. 빈 입력이면 빈 맵
	 * @throws IllegalArgumentException {@code keyTerms} 가 1 ~ {@link #MAX_KEY_TERMS} 밖일 때
	 */
	Map<EventId, EventProfile> profiles(Collection<EventId> ids, int keyTerms);

	/**
	 * 사건과 가까운 사건 — 같은 모델 버전이고 합쳐지지 않았고(열림 · 닫힘), 첫 ~ 마지막 기사 시각이 {@code [from, to]} 와 겹치고, 중심의
	 * 코사인 유사도가 {@code minCosine} 이상인 사건(event-R24). 그 사건 자신과 코사인을 셀 수 없는 사건(영 벡터 중심)은 빠진다. 코사인
	 * 내림차순, 사건 ID 순으로 {@code limit} 개까지.
	 * @return 그 사건이 합쳐졌거나 없으면 빈 목록
	 * @throws IllegalArgumentException {@code from} 이 {@code to} 보다 늦거나, {@code keyTerms} 가 1 ~
	 * {@link #MAX_KEY_TERMS} 밖이거나, {@code limit} 이 1 ~ {@link #MAX_NEIGHBORS} 밖일 때
	 */
	List<EventNeighbor> neighbors(EventId id, Instant from, Instant to, double minCosine, int keyTerms, int limit);

	/**
	 * 말 수 — 첫 ~ 마지막 기사 시각이 {@code [from, to]} 와 겹치는 합쳐지지 않은 사건(열림 · 닫힘)의 수와, 그 가운데 말마다 그 말을 핵심 말로
	 * 가진 사건 수(event-R24). 모델 버전을 가리지 않는다. 겹친 말은 한 번만 센다.
	 * @return 물은 말마다 수 — 어느 사건에도 없는 말은 0. 빈 입력이면 사건 수와 빈 맵
	 * @throws IllegalArgumentException {@code from} 이 {@code to} 보다 늦거나, 서로 다른 말이 {@link #MAX_TERMS} 개를 넘을 때
	 */
	EventTermCounts termCounts(Collection<String> terms, Instant from, Instant to);

	/**
	 * 마지막 기사 시각이 {@code since} 이후(같으면 포함)인, 합쳐지지 않은 사건 — 첫 기사 시각 · 사건 ID 순(event-R24). 스토리라인 채우기가
	 * 쓴다.
	 */
	List<EventView> lastArticleSince(Instant since);

}
