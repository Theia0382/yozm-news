package net.yozmnews.digest.domain.output;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 읽은 추가 헤드라인 하나 — 인용은 별칭
 */
public record HeadlineDraft(String ref, @Nullable String note) {

	public HeadlineDraft {
		Objects.requireNonNull(ref, "ref");
	}

}
