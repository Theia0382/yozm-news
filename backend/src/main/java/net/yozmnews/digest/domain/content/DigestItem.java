package net.yozmnews.digest.domain.content;

import java.util.Objects;

import net.yozmnews.digest.SelectionReason;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 섹션의 인용 한 줄 — 노출 기록 · docs/specs/digest.md §6
 *
 * @param articleId 인용한 기사 — 항목의 표본 기사
 * @param selectionReason 그 기사가 든 사건의 선정 이유
 */
@Table("digest_items")
public record DigestItem(long articleId, SelectionReason selectionReason) {

	public DigestItem {
		if (articleId < 1) {
			throw new IllegalArgumentException("기사 식별자는 1 이상이어야 한다: " + articleId);
		}
		Objects.requireNonNull(selectionReason, "selectionReason");
	}

}
