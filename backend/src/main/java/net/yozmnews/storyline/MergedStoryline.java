package net.yozmnews.storyline;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.evaluation.RelationJudgementId;
import org.jspecify.annotations.Nullable;

/**
 * 다른 스토리라인에 합쳐진 스토리라인 하나 — docs/specs/storyline.md §3 · storyline-R18 · storyline-R34.
 *
 * @param id 합쳐진 스토리라인 ID
 * @param mergedAt 합친 시각
 * @param judgement 같은 사안의 조각으로 합쳤으면 그 관계 판정 원본 ID — 정돈이 합쳤으면 null. 원본은 먼저 지워졌을
 * 수 있다
 * @param criteria 그 합치기에 쓴 관계 기준 — 판정과 함께 있거나 함께 없다
 */
public record MergedStoryline(StorylineId id, Instant mergedAt, @Nullable RelationJudgementId judgement,
		@Nullable StorylineRelationCriteria criteria) {

	/**
	 * @throws IllegalArgumentException 판정과 기준 가운데 하나만 있을 때
	 */
	public MergedStoryline {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(mergedAt, "mergedAt");
		if ((judgement == null) != (criteria == null)) {
			throw new IllegalArgumentException("합치기의 판정과 기준은 함께 있거나 함께 없다: " + id);
		}
	}

}
