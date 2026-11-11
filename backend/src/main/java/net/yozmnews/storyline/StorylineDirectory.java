package net.yozmnews.storyline;

import java.util.List;
import java.util.Optional;

import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.event.EventView;
import org.jspecify.annotations.Nullable;

/**
 * 다른 모듈이 스토리라인을 읽는 조회 — 스토리라인 · 스토리라인의 사건(타임라인) · 사건의 스토리라인 · 조회 기간의 스토리라인 목록 · 큰
 * 스토리라인 · 소속 없는 사건 · docs/specs/storyline.md §3 · storyline-R19 · storyline-R21 ~ R23.
 * <p>
 * 사용자 정보를 받지 않는다. 트랜잭션을 열지 않는다 — 각 질의는 그 순간의 값이고, 사건 · 기간 집계는 {@code event} 의 공개 조회로 따로 읽는다.
 * 모듈 잠금을 잡지 않아 질의 사이에 소속이 바뀔 수 있다.
 */
public interface StorylineDirectory {

	/** 스토리라인 목록 한 쪽의 상한 — 정본. 측정 전 목표 */
	int MAX_PAGE_SIZE = 100;

	/**
	 * 스토리라인 하나 — 합쳐진 스토리라인(storyline-R18)이면 남는 스토리라인을 낸다.
	 * @return 없는 스토리라인이면 빈 값
	 */
	Optional<StorylineView> find(StorylineId id);

	/**
	 * 스토리라인의 구성 사건(타임라인) — 첫 기사 시각 · 사건 ID 순. 합쳐진 스토리라인이면 남는 스토리라인의 것이다. 합쳐졌거나 지워졌는데 아직
	 * 소속을 따르지 않은 사건은 빠진다(storyline-R21).
	 * @return 없는 스토리라인이면 빈 목록
	 */
	List<EventView> events(StorylineId id);

	/**
	 * 사건이 든 스토리라인 — 스토리라인 ID 순. 합쳐진 사건이면 남는 사건의 것이다(storyline-R21).
	 * @return 없는 사건이면 빈 목록
	 */
	List<StorylineView> storylinesOf(EventId event);

	/**
	 * 기간의 스토리라인(조회 기간 안 기사가 있는 구성 사건이 하나라도 있는, 합쳐지지 않은 스토리라인) — 마지막 기사 시각 내림차순, 스토리라인 ID
	 * 내림차순(storyline-R22). 집계 · 큰 여부는 이 기간 · 지금 기준. 쪽 사이에 마지막 기사 시각이 바뀐 스토리라인은 건너뛰거나 다시 나올 수 있다.
	 * @param after 앞 쪽의 {@link StorylinePage#next()} — 첫 쪽이면 null
	 * @throws IllegalArgumentException {@code size} 가 1 ~ {@link #MAX_PAGE_SIZE} 밖일 때
	 * @throws IllegalStateException 쪽에 스토리라인이 있는데 큰 판정 기준의 처음 값을 적재하기 전일 때
	 */
	StorylinePage list(EventPeriod period, @Nullable StorylineCursor after, int size);

	/**
	 * 기간의 스토리라인 가운데 큰 스토리라인 — 지금 기준으로 판정한다(storyline-R19). 소스 수 내림차순, 중요도 평균 내림차순, 스토리라인 ID 순.
	 * @throws IllegalStateException 큰 판정 기준의 처음 값을 적재하기 전일 때
	 */
	List<BigStoryline> bigStorylines(EventPeriod period);

	/** 조회 기간 안 기사가 있는 사건 가운데 소속 없는 사건의 수와 그 사건들의 기간 안 기사 수(storyline-R23) */
	UnlinkedEvents unlinked(EventPeriod period);

}
