package net.yozmnews.event.domain.event;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.assignment.EventSnapshot;

/**
 * 사건 저장소 — 사건 행과 사건 핵심 말 · docs/specs/event.md §4.
 */
public interface EventRepository {

	/**
	 * 후보 사건을 찾는다 — 열린 사건 가운데 모델 버전이 같고 구성 기사의 발행 시각 범위가 {@code [from, to]} 와 겹치는 것. 중심과의 코사인
	 * 유사도는 인덱스 없이 정확히 센다. 핵심 말은 기사 수가 많은 순(같으면 말 순) 상위
	 * {@link net.yozmnews.event.domain.assignment.CandidateScoring#EVENT_KEY_TERMS} 개다.
	 * @param vector 기사 벡터 — 같은 모델 버전의 표현
	 * @return 사건 ID 순
	 */
	List<EventSnapshot> candidates(String modelVersion, float[] vector, Instant from, Instant to);

	/**
	 * 사건 소속 판정에 넘길 서술 기사를 고른다 — 그 모델 버전의 표현이 있는 구성 기사 가운데 사건 중심에 가까운 순(같으면 기사 ID 순).
	 */
	List<ArticleId> describers(long eventId, String modelVersion, int limit);

	/** 사건 하나를 잠근다({@code FOR UPDATE}) — 없으면 빈 값. 트랜잭션 안에서 부른다 */
	Optional<LockedEvent> lock(long eventId);

	/** 사건들을 사건 ID 순으로 잠근다 — 없는 사건은 결과에 없다. 트랜잭션 안에서 부른다 */
	List<LockedEvent> lockAll(Collection<Long> eventIds);

	/**
	 * 새 사건을 연다 — 열린 상태, 기사 하나, 중심은 그 기사 벡터, 첫 · 마지막 기사 시각은 그 기사의 발행 시각, 연 시각 · 마지막으로 붙은
	 * 시각은 {@code now}.
	 * @param keyTerms 그 기사의 핵심 말 — 같은 말은 한 번 센다
	 * @return 새 사건 ID
	 */
	long open(String modelVersion, float[] centroid, Instant publishedAt, List<String> keyTerms, Instant now);

	/**
	 * 잠근 사건을 고쳐 쓴다 — 중심 · 기사 수 · 첫 · 마지막 기사 시각 · 마지막으로 붙은 시각, 그리고 {@code revision} 을 하나 늘린다(병합
	 * 회차가 바뀐 사건을 고른다). 핵심 말은 더한 말 목록에 나온 수만큼 늘리고, 뺀 말 목록에 나온 수만큼
	 * 줄여 0 이 되면 지운다. 기사 하나의 핵심 말에는 같은 말이 없으므로, 기사마다 이어 붙인 목록이면 말마다 그 말을 가진 기사 수만큼 바뀐다.
	 */
	void update(LockedEvent next, List<String> addedTerms, List<String> removedTerms);

	/**
	 * 사건과 그 핵심 말을 지운다 — 그 사건에 합쳐진 사건 행, 이 사건들을 가리키는 배정 후보와 병합 판정 기록도 함께. 그 사건의 배정이 먼저
	 * 지워지거나 옮겨져 있어야 한다
	 */
	void delete(long eventId);

	/**
	 * 병합 회차가 볼 바뀐 열린 사건 — 지난 확인 뒤 고쳐졌거나({@code revision}) 다른 기준으로 확인한 사건. 잠그지 않는다.
	 * @return 사건 ID 순, {@code limit} 개까지
	 */
	List<MergeSubject> mergeDue(long criteriaId, int limit);

	/** 병합 확인을 표시한다 — 사건의 {@code revision} 이 읽은 값 그대로일 때만. 그새 바뀌었으면 다음 회차가 다시 본다 */
	void markMergeChecked(long eventId, long revision, long criteriaId);

	/**
	 * 잠근 두 사건을 합쳐 쓴다 — 트랜잭션 안에서, 합쳐질 사건의 배정을 남는 사건으로 옮긴 뒤 부른다. 남는 사건에 합친 값을 쓰고(연 시각 포함,
	 * {@code revision} + 1), 합쳐질 사건의 핵심 말 수를 남는 사건에 더한 뒤 지운다. 합쳐질 사건은 {@code MERGED} 로 남는 사건을 가리키고
	 * 열림이 끝난 시각은 {@code now} 다. 합쳐질 사건을 가리키던 합쳐진 사건 행도 남는 사건을 가리킨다.
	 * @param survivorNext 남는 사건의 합친 값 — {@link LockedEvent#absorbed}
	 */
	void absorb(LockedEvent survivorNext, LockedEvent absorbed, Instant now);

}
