package net.yozmnews.digest.domain.output;

import java.util.Objects;

import net.yozmnews.digest.domain.candidate.AliasedItem;
import org.jspecify.annotations.Nullable;

/**
 * 항목과 대조한 헤드라인 — 인용은 항목의 표본 기사다
 *
 * @param item 헤드라인 항목
 * @param title 제목
 * @param note 짧은 코멘트 — 없으면 null
 */
public record CitedHeadline(AliasedItem item, String title, @Nullable String note) {

	public CitedHeadline {
		Objects.requireNonNull(item, "item");
		Objects.requireNonNull(title, "title");
	}

}
