package net.yozmnews.digest.domain.content;

import java.util.Objects;

import net.yozmnews.digest.domain.candidate.SelectionReason;
import org.jspecify.annotations.Nullable;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 섹션의 인용 한 줄 — 노출 기록 · docs/specs/digest.md §6
 */
@Table("digest_items")
public record DigestItem(long articleId, @Nullable String note, SelectionReason selectionReason) {

	public DigestItem {
		if (articleId < 1) {
			throw new IllegalArgumentException("기사 식별자는 1 이상이어야 한다: " + articleId);
		}
		Objects.requireNonNull(selectionReason, "selectionReason");
	}

}
