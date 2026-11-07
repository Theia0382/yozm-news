package net.yozmnews.event;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import org.jspecify.annotations.Nullable;

/**
 * 다른 모듈이 사건을 읽는 조회 — 사건 · 사건의 기사 · 기사의 사건 · 대표 기사 · 큰 사건 · 사건 목록 · docs/specs/event.md §3 ·
 * event-R14 · event-R15 · event-R20.
 * <p>
 * 사용자 정보를 받지 않는다. 트랜잭션을 열지 않는다 — 각 질의는 그 순간의 값이고, 큰 사건의 재료(소스 · 중요도)는 다른 모듈의 공개 조회로
 * 따로 읽는다.
 */
public interface EventDirectory {

	/** 사건 목록 한 쪽의 상한 — 정본. 측정 전 목표 */
	int MAX_PAGE_SIZE = 100;

	/**
	 * 사건 하나. 계약: 합쳐진 사건이면 남는 사건을 낸다 — 사건 병합이 생기기 전이라 지금은 그 사건 그대로다.
	 * @return 없는 사건이면 빈 값
	 */
	Optional<EventView> find(EventId id);

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
	 * 기간 안의 큰 사건 — 지금 기준으로 판정한다(event-R15). 소스 수 내림차순, 중요도 내림차순, 사건 ID 순.
	 * @throws IllegalStateException 큰 사건 기준의 처음 값을 적재하기 전일 때
	 */
	List<BigEvent> bigEvents(EventPeriod period);

	/**
	 * 기간 안 기사가 있는 사건 — 마지막 기사 시각 내림차순, 사건 ID 내림차순. 집계 · 큰 사건 여부는 이 기간, 대표 기사는 사건 기사 전체에서.
	 * 쪽 사이에 마지막 기사 시각이 바뀐 사건은 건너뛰거나 다시 나올 수 있다.
	 * @param after 앞 쪽의 {@link EventPage#next()} — 첫 쪽이면 null
	 * @throws IllegalArgumentException {@code size} 가 1 ~ {@link #MAX_PAGE_SIZE} 밖일 때
	 * @throws IllegalStateException 쪽에 사건이 있는데 큰 사건 기준의 처음 값을 적재하기 전일 때
	 */
	EventPage list(EventPeriod period, @Nullable EventCursor after, int size);

}
