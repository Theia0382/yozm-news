package net.yozmnews.event.domain.assignment;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

import net.yozmnews.evaluation.EventJudgementId;
import org.jspecify.annotations.Nullable;

/**
 * 병합 판정 기록 저장소 — 띠에 든 사건 쌍의 사건 소속 판정 확률. 쌍은 순서가 없다 · docs/specs/event.md §4 · event-R13.
 * <p>
 * 한 쌍은 한 번만 묻는다 — 사건이 바뀌어도 다시 묻지 않는다. 쌍의 사건을 지우면 사건 저장소가 함께 지운다.
 */
public interface MergeJudgementRepository {

	/** 사건 하나와 상대 사건들의 판정 확률 — 기록이 있는 상대만, 상대 사건 ID → 확률 */
	Map<Long, Double> find(long eventId, Collection<Long> others);

	/**
	 * 사건 하나와 상대 사건들의 판정 확률을 남긴다 — 그 쌍의 기록이 이미 있으면 그대로.
	 * @param judgement 확률을 낸 사건 소속 판정 원본 — 새로 넣는 쌍에 남긴다. 배정 때 받은 확률을 옮기면 null(그 원본은 배정이 가리킨다)
	 */
	void save(long eventId, Map<Long, Double> probabilities, @Nullable EventJudgementId judgement, Instant now);

}
