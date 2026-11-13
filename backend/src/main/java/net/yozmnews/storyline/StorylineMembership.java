package net.yozmnews.storyline;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.event.EventId;
import org.jspecify.annotations.Nullable;

/**
 * 소속 하나와 그 판정 근거 — docs/specs/storyline.md §3 · storyline-R14 · storyline-R34.
 *
 * @param storyline 그 사건을 직접 가진 스토리라인 — 조회한 스토리라인이거나 그 하위
 * @param score 점수 · 코사인 · 겹침 — {@link LinkedBy#MERGE} 면 null
 * @param criteria 그 판정에 쓴 잇기 기준 — {@link LinkedBy#MERGE} 면 null
 */
public record StorylineMembership(StorylineId storyline, EventId event, LinkedBy linkedBy, @Nullable LinkScore score,
		@Nullable StorylineLinkingCriteria criteria, Instant linkedAt) {

	public StorylineMembership {
		Objects.requireNonNull(storyline, "storyline");
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(linkedBy, "linkedBy");
		Objects.requireNonNull(linkedAt, "linkedAt");
	}

}
