package net.yozmnews.digest.domain.content;

import org.springframework.data.relational.core.mapping.Table;

/**
 * 섹션의 인용 한 줄 — 항목의 표본 기사(화면 출처 목록) · docs/specs/digest.md §6. 선정 이유는 사건마다 {@link SectionEvent} 에 있다
 *
 * @param articleId 인용한 기사 — 항목의 표본 기사
 */
@Table("digest_items")
public record DigestItem(long articleId) {

	public DigestItem {
		if (articleId < 1) {
			throw new IllegalArgumentException("기사 식별자는 1 이상이어야 한다: " + articleId);
		}
	}

}
