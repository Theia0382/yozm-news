package net.yozmnews.digest.domain.candidate;

import java.util.Objects;

import net.yozmnews.article.ArticleId;

/**
 * 다이제스트 후보 하나 — 기사와 선정 이유 · docs/specs/digest.md §4
 */
public record DigestCandidate(ArticleId articleId, SelectionReason reason) {

	public DigestCandidate {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(reason, "reason");
	}

}
