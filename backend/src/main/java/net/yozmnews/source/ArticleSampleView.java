package net.yozmnews.source;

import java.time.Instant;
import java.util.Objects;

/**
 * 미리보기의 기사 표본 하나 — docs/specs/source.md §3.
 * <p>
 * 판정에는 쓰지 않는다.
 */
public record ArticleSampleView(String title, Instant publishedAt, boolean publishedAtEstimated) {

	public ArticleSampleView {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(publishedAt, "publishedAt");
	}

}
