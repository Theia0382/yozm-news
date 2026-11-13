package net.yozmnews.storyline;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.evaluation.RelationJudgementId;
import org.jspecify.annotations.Nullable;

/**
 * 물은 기록 하나 — 쌍마다 마지막으로 물은 결말 · docs/specs/storyline.md §3 · storyline-R30 · storyline-R34.
 *
 * @param other 쌍의 상대 스토리라인
 * @param smallerEvents 물었을 때 작은 쪽의 구성 사건 수
 * @param judgement 판정 원본 ID — 판정 원본이 남지 않은 결말({@link RelationOutcome#NOTHING_TO_ASK} ·
 * {@link RelationOutcome#REJECTED})이면 null
 * @param criteria 그때의 관계 기준
 */
public record StorylineRelationAsked(StorylineId other, int smallerEvents, RelationOutcome outcome,
		@Nullable RelationJudgementId judgement, StorylineRelationCriteria criteria, Instant askedAt) {

	public StorylineRelationAsked {
		Objects.requireNonNull(other, "other");
		Objects.requireNonNull(outcome, "outcome");
		Objects.requireNonNull(criteria, "criteria");
		Objects.requireNonNull(askedAt, "askedAt");
	}

}
