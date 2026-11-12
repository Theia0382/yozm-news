package net.yozmnews.storyline;

import java.time.Instant;
import java.util.Objects;

/**
 * 스토리라인 사이 관계 하나 · docs/specs/storyline.md §3 · storyline-R25.
 *
 * @param source 포함의 상위 · 이어짐의 앞
 * @param target 포함의 하위 · 이어짐의 뒤
 * @param linkedAt 그 관계를 맺은(마지막으로 고친) 시각
 */
public record StorylineRelationView(StorylineRelationKind kind, StorylineId source, StorylineId target,
		Instant linkedAt) {

	public StorylineRelationView {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(linkedAt, "linkedAt");
	}

}
