package net.yozmnews.event.domain.event;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventArticleView;
import net.yozmnews.event.EventView;
import net.yozmnews.event.JudgementUse;
import net.yozmnews.event.MergePair;
import net.yozmnews.event.MergedEvent;
import org.jspecify.annotations.Nullable;

/**
 * 사건 조회 저장소 — 공개 조회의 읽기 전용 질의 · docs/specs/event.md §4 · event-R14 · event-R20.
 * <p>
 * 배정 쓰기용 {@link EventRepository} 와 나눈다. 기간은 배정의 발행 시각으로 고른다 — 시작 포함 · 끝 제외.
 */
public interface EventQueryRepository {

	/** 사건 하나 — 합쳐진 사건이면 남는 사건, 없는 사건이면 빈 값 */
	Optional<EventView> find(long eventId);

	/** 사건의 배정 전부 — 발행 시각 · 기사 ID 순 */
	List<EventArticleView> articles(long eventId);

	/** 기사 → 사건 ID — 배정이 없는 기사는 빠진다 */
	Map<ArticleId, Long> eventsOf(Collection<ArticleId> articleIds);

	/** 주어진 (사건, 기사) 가운데 사건마다 대표 기사 — 그 사건의 배정이 아닌 쌍은 무시한다 */
	Map<Long, ArticleId> representatives(Map<Long, ? extends Collection<ArticleId>> among);

	/** 사건 기사 전체에서 사건마다 대표 기사 — 닫을 때 남긴 대표가 있으면 그것 */
	Map<Long, ArticleId> representativesOfAll(Collection<Long> eventIds);

	/** 기간 안 배정 — 사건 ID 순, 사건마다 발행 시각 · 기사 ID 순 */
	Map<Long, List<ArticleId>> membersIn(Instant from, Instant to);

	/** 주어진 사건들의 기간 안 배정 — 사건 ID 순, 사건마다 발행 시각 · 기사 ID 순 */
	Map<Long, List<ArticleId>> membersIn(Collection<Long> eventIds, Instant from, Instant to);

	/**
	 * 기간 안 배정이 있는 사건 — 마지막 기사 시각 내림차순, 사건 ID 내림차순.
	 * @param afterLast 커서의 마지막 기사 시각 — 첫 쪽이면 null(그때 {@code afterId} 도 null)
	 * @param limit 가져올 행 수
	 */
	List<EventView> page(Instant from, Instant to, @Nullable Instant afterLast, @Nullable Long afterId, int limit);

	/** 이 사건을 가리키는 합쳐진 사건 — 합친 시각 · 사건 ID 순. 확률은 (그 사건, 이 사건) 쌍의 병합 판정 기록 */
	List<MergedEvent> mergedInto(long survivor);

	/**
	 * 판정 원본을 가리키는 배정 — 원본 ID → 그 기사의 지금 배정.
	 * @throws IllegalStateException 한 원본을 배정 둘이 가리킬 때
	 */
	Map<Long, JudgementUse.Assignment> assignmentsByJudgement(Collection<Long> judgementIds);

	/** 판정 원본을 가리키는 병합 판정 기록 — 원본 ID → 쌍들((작은 ID, 큰 ID) 순)과 지금 같은 사건인지 */
	Map<Long, List<MergePair>> mergePairsByJudgement(Collection<Long> judgementIds);

}
