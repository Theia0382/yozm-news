package net.yozmnews.source.domain.gate;

import java.time.Instant;
import java.util.Objects;

/**
 * 미리보기의 기사 표본 — 판정에는 쓰지 않는다.
 *
 * @param publishedAtEstimated 발행 시각을 해석하지 못해 받은 시각으로 채웠다
 */
public record ArticleSample(String title, Instant publishedAt, boolean publishedAtEstimated) {

	public ArticleSample {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(publishedAt, "publishedAt");
	}

}
