package net.yozmnews.storyline;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPeriod;
import org.jspecify.annotations.Nullable;

/**
 * 다른 모듈이 스토리라인을 읽는 조회 — 스토리라인 · 스토리라인의 사건(타임라인) · 사건의 스토리라인 · 사건마다 포함 나무의 꼭대기 · 조회 기간의
 * 스토리라인 목록 · 큰 스토리라인 · 소속 없는 사건 · 스토리라인의 관계 · docs/specs/storyline.md §3 · storyline-R19 · storyline-R21 ~ R23 · storyline-R33 · storyline-R37.
 * <p>
 * 스토리라인의 사건 · 집계는 닫은 사건 집합으로 본다 — 직접 소속과, 포함을 따라 내려간 모든 하위의 직접 소속을 합친 것이다. 사용자 정보를
 * 받지 않는다. 트랜잭션을 열지 않는다 — 각 질의는 그 순간의 값이고, 사건 · 기간 집계는 {@code event} 의 공개 조회로 따로 읽는다. 모듈
 * 잠금을 잡지 않아 질의 사이에 소속 · 관계가 바뀔 수 있다.
 */
public interface StorylineDirectory {

	/** 스토리라인 목록 한 쪽의 상한 — 정본. 측정 전 목표 */
	int MAX_PAGE_SIZE = 100;

	/** 꼭대기 조회 한 번에 묻는 서로 다른 사건 수의 상한 — 정본. 측정 전 목표 */
	int MAX_TOP_EVENTS = 1000;

	/** 스토리라인 꼭대기 조회 한 번에 묻는 서로 다른 스토리라인 수의 상한 — 정본. 측정 전 목표 */
	int MAX_TOP_STORYLINES = 1000;

	/**
	 * 스토리라인 하나 — 합쳐진 스토리라인(storyline-R18)이면 남는 스토리라인을 낸다.
	 * @return 없는 스토리라인이면 빈 값
	 */
	Optional<StorylineView> find(StorylineId id);

	/**
	 * 스토리라인 여럿 — 키는 받은 ID, 값은 그 ID 가 가리키는 스토리라인이다. 합쳐진 스토리라인(storyline-R18)이면 남는 스토리라인을 낸다
	 * (storyline-R21). 받은 ID 여럿이 같은 남는 스토리라인을 가리키면 그 값이 여러 키에 실린다.
	 * @return 없는 스토리라인(지웠거나 처음부터 없는 ID)은 결과에 없다. 빈 입력이면 빈 맵
	 */
	Map<StorylineId, StorylineView> findAll(Collection<StorylineId> ids);

	/**
	 * 스토리라인의 닫은 사건 집합(타임라인) — 사건마다 한 번씩, 첫 기사 시각 · 사건 ID 순. 사건마다 직접 소속인지 싣는다(storyline-R33).
	 * 합쳐진 스토리라인이면 남는 스토리라인의 것이다. 합쳐졌거나 지워졌는데 아직 소속을 따르지 않은 사건은 빠진다(storyline-R21).
	 * @return 없는 스토리라인이면 빈 목록
	 */
	List<StorylineEvent> events(StorylineId id);

	/**
	 * 사건이 든 스토리라인 — 직접 든 스토리라인과 그 상위 전부를 한 번씩, 스토리라인 ID 순. 스토리라인마다 직접 든 것인지
	 * 싣는다(storyline-R33). 합쳐진 사건이면 남는 사건의 것이다(storyline-R21).
	 * @return 없는 사건이면 빈 목록
	 */
	List<EventStoryline> storylinesOf(EventId event);

	/**
	 * 사건마다 그 사건이 걸친 포함 나무의 꼭대기 — 직접 든 합쳐지지 않은 스토리라인에서 포함의 상위를 끝까지 따른 뿌리(상위가 없으면 자기).
	 * 이어짐은 따르지 않는다. 사건마다 꼭대기는 그 꼭대기 아래 직접 소속의 가장 높은 소속 점수 내림차순이다 — 점수 없는 소속(합치기로 옮긴 것)만
	 * 있는 꼭대기는 뒤, 같으면 꼭대기 ID 오름차순. 점수는 싣지 않는다(storyline-R21). 큰 판정 행의 머리({@link BigStoryline#storyline()})는
	 * 그 나무의 꼭대기와 같다.
	 * @return 사건 → 꼭대기(순서대로, 비지 않음). 소속이 없는 사건 · 합쳐졌거나 없는 사건은 빠진다. 빈 입력이면 빈 맵
	 * @throws IllegalArgumentException 서로 다른 사건이 {@link #MAX_TOP_EVENTS} 를 넘을 때
	 */
	Map<EventId, List<StorylineId>> topsOf(Collection<EventId> events);

	/**
	 * 스토리라인마다 그 포함 나무의 꼭대기 — 포함의 상위를 끝까지 따른 뿌리(상위가 없으면 자기). 이어짐은 따르지 않는다. 합쳐진
	 * 스토리라인(storyline-R18)이면 남는 스토리라인의 꼭대기다(storyline-R37).
	 * @return 받은 스토리라인 ID → 꼭대기. 없는 스토리라인(지웠거나 처음부터 없는 ID)은 빠진다. 빈 입력이면 빈 맵
	 * @throws IllegalArgumentException 서로 다른 스토리라인이 {@link #MAX_TOP_STORYLINES} 를 넘을 때
	 */
	Map<StorylineId, StorylineId> topsOfStorylines(Collection<StorylineId> storylines);

	/**
	 * 스토리라인마다 그 포함 나무의 꼭대기에서 이어짐을 앞 → 뒤로 끝까지 따라 닿는 꼭대기. 이어짐의 두 끝은 포함 나무의 꼭대기로 올려
	 * 보고, 뒤 → 앞으로는 따르지 않는다. 합쳐진 스토리라인(storyline-R18)이면 남는 스토리라인의 것이다(storyline-R38).
	 * @return 받은 스토리라인 ID → 닿는 꼭대기(자기 꼭대기는 없다 — 닿는 것이 없으면 빈 집합). 없는 스토리라인(지웠거나 처음부터 없는
	 *         ID)은 빠진다. 빈 입력이면 빈 맵
	 * @throws IllegalArgumentException 서로 다른 스토리라인이 {@link #MAX_TOP_STORYLINES} 를 넘을 때
	 */
	Map<StorylineId, Set<StorylineId>> continuationsOf(Collection<StorylineId> storylines);

	/**
	 * 기간의 스토리라인(닫은 사건 집합에 조회 기간 안 기사가 있는 사건이 하나라도 있는, 합쳐지지 않은 스토리라인) — 직접 소속의 마지막 기사 시각
	 * 내림차순, 스토리라인 ID 내림차순(storyline-R22). 집계 · 큰 여부는 이 기간 · 지금 기준이고 닫은 사건 집합으로 센다. 쪽 사이에 마지막
	 * 기사 시각이 바뀐 스토리라인은 건너뛰거나 다시 나올 수 있다.
	 * @param after 앞 쪽의 {@link StorylinePage#next()} — 첫 쪽이면 null
	 * @throws IllegalArgumentException {@code size} 가 1 ~ {@link #MAX_PAGE_SIZE} 밖일 때
	 * @throws IllegalStateException 쪽에 스토리라인이 있는데 큰 판정 기준의 처음 값을 적재하기 전일 때
	 */
	StorylinePage list(EventPeriod period, @Nullable StorylineCursor after, int size);

	/**
	 * 기간의 스토리라인 가운데 큰 스토리라인 — 지금 기준으로 판정한다(storyline-R19). 포함 나무마다 한 행이다 — 큰 스토리라인
	 * 가운데 그 나무에서 가장 위의 것이 행이고, 그 아래의 큰 스토리라인은 행의 {@link BigStoryline#branches()} 에 실린다. 행과
	 * 아래 행 모두 보도 강도 내림차순, 스토리라인 ID 순.
	 * @throws IllegalStateException 큰 판정 기준의 처음 값을 적재하기 전일 때
	 */
	List<BigStoryline> bigStorylines(EventPeriod period);

	/** 조회 기간 안 기사가 있는 사건 가운데 소속 없는 사건의 수와 그 사건들의 기간 안 기사 수(storyline-R23) */
	UnlinkedEvents unlinked(EventPeriod period);

	/**
	 * 스토리라인이 앞이거나 뒤인 관계 — 종류(포함 먼저) · 앞 ID · 뒤 ID 순(storyline-R25). 합쳐진 스토리라인이면 남는 스토리라인의 것이다.
	 * @return 없는 스토리라인이면 빈 목록
	 */
	List<StorylineRelationView> relationsOf(StorylineId id);

}
