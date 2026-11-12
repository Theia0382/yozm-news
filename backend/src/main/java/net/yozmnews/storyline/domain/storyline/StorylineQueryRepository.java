package net.yozmnews.storyline.domain.storyline;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * 스토리라인 조회 저장소 — 공개 조회의 읽기 전용 질의 · docs/specs/storyline.md §4 · storyline-R21 · storyline-R22.
 * <p>
 * 쓰기용 {@link StorylineRepository} 와 나눈다. 구성 사건 · 소속 여부는 {@link StorylineRepository#members} ·
 * {@link StorylineRepository#affiliated} 를 쓴다.
 */
public interface StorylineQueryRepository {

	/** 스토리라인 하나 — 합쳐진 행이면 남는 행, 없으면 빈 값 */
	Optional<StoredStoryline> find(long storylineId);

	/** 이 사건 가운데 하나라도 든, 합쳐지지 않은 스토리라인 — 스토리라인 ID 순. 빈 입력이면 빈 목록 */
	List<StoredStoryline> having(Collection<Long> eventIds);

	/**
	 * 이 사건 가운데 하나라도 든, 합쳐지지 않은 스토리라인 — 마지막 기사 시각 내림차순, 스토리라인 ID 내림차순. 빈 입력이면 빈 목록.
	 * @param afterLast 커서의 마지막 기사 시각 — 첫 쪽이면 null(그때 {@code afterId} 도 null)
	 * @param limit 가져올 행 수
	 */
	List<StoredStoryline> page(Collection<Long> eventIds, @Nullable Instant afterLast, @Nullable Long afterId, int limit);

}
