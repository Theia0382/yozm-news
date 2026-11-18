package net.yozmnews.digest.domain.interest;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.article.ArticleId;

/**
 * 노출 행 하나 — 그 사용자의 다이제스트 하나가 기사 하나를 인용했다. 같은 기사가 여러 다이제스트에 있으면 여러 행이다. docs/specs/digest.md §5 · digest-R24
 *
 * @param article 인용한 기사
 * @param exposedAt 그 다이제스트를 끝낸 시각
 */
public record ExposureRow(ArticleId article, Instant exposedAt) {

	public ExposureRow {
		Objects.requireNonNull(article, "article");
		Objects.requireNonNull(exposedAt, "exposedAt");
	}

}
