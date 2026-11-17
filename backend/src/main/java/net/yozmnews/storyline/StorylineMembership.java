package net.yozmnews.storyline;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.evaluation.FlowMembershipJudgementId;
import net.yozmnews.event.EventId;
import org.jspecify.annotations.Nullable;

/**
 * 소속 하나와 그 판정 근거 — docs/specs/storyline.md §3 · storyline-R14 · storyline-R34 · storyline-R36.
 *
 * @param storyline 그 사건을 직접 가진 스토리라인 — 조회한 스토리라인이거나 그 하위
 * @param score 점수 · 코사인 · 겹침 — {@link LinkedBy#MERGE} 면 null
 * @param criteria 그 판정에 쓴 잇기 기준 — {@link LinkedBy#MERGE} 면 null
 * @param probability 띠 안에서 흐름 소속 판정으로 정했으면 그 확률 — 위 기준 이상으로 정했거나 {@link LinkedBy#MERGE} 면 null
 * @param judgement 그 흐름 소속 판정의 원본 ID — 확률과 함께 있거나 함께 없다. 원본은 먼저 지워졌을 수 있다
 */
public record StorylineMembership(StorylineId storyline, EventId event, LinkedBy linkedBy, @Nullable LinkScore score,
		@Nullable StorylineLinkingCriteria criteria, @Nullable Double probability,
		@Nullable FlowMembershipJudgementId judgement, Instant linkedAt) {

	public StorylineMembership {
		Objects.requireNonNull(storyline, "storyline");
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(linkedBy, "linkedBy");
		Objects.requireNonNull(linkedAt, "linkedAt");
	}

}
