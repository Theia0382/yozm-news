package net.yozmnews.digest;

import java.util.Objects;

import net.yozmnews.article.ArticleId;
import org.jspecify.annotations.Nullable;

/**
 * 섹션이 인용한 기사 한 줄 — 기사 ID 와 선정 이유. 링크 · 출처 · 발행 시각은 싣지 않는다 — 화면이 {@code article} 에서 읽는다.
 * docs/specs/digest.md §3 · digest-R7 · digest-R38
 *
 * @param article 기사 ID
 * @param note 추가 헤드라인의 코멘트 — 없으면 null
 * @param reason 선정 이유
 */
public record DigestCitation(ArticleId article, @Nullable String note, SelectionReason reason) {

	public DigestCitation {
		Objects.requireNonNull(article, "article");
		Objects.requireNonNull(reason, "reason");
	}

}
