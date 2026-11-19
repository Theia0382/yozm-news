package net.yozmnews.digest;

import java.util.Objects;

import net.yozmnews.article.ArticleId;

/**
 * 섹션이 인용한 기사(출처 기사) 하나 — 기사 ID. 링크 · 출처 · 발행 시각은 싣지 않는다 — 화면이 {@code article} 에서 읽는다. 선정
 * 이유는 섹션에 하나다({@link DigestSectionView#reason()}). docs/specs/digest.md §3 · digest-R7 · digest-R38
 *
 * @param article 기사 ID
 */
public record DigestCitation(ArticleId article) {

	public DigestCitation {
		Objects.requireNonNull(article, "article");
	}

}
