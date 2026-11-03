package net.yozmnews.evaluation;

import java.time.Instant;
import java.util.Objects;

/**
 * 태그 체계 버전 하나 — 번호 · 활성인가 · 만든 행위자 · 시각 — docs/specs/evaluation.md §3
 */
public record TagSetVersionView(TagSetId id, int number, boolean active, ChangeActor createdBy, Instant createdAt) {

	public TagSetVersionView {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(createdBy, "createdBy");
		Objects.requireNonNull(createdAt, "createdAt");
	}

}
