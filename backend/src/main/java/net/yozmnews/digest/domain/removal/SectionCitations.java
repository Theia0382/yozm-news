package net.yozmnews.digest.domain.removal;

import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.SectionKind;

/**
 * 섹션 하나와 그 섹션이 인용한 기사 — 지운 기사 따르기의 입력
 */
public record SectionCitations(long sectionId, long digestId, SectionKind kind, Set<ArticleId> cited) {

	public SectionCitations {
		Objects.requireNonNull(kind, "kind");
		cited = Set.copyOf(cited);
	}

}
