package net.yozmnews.digest.domain.output;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 읽은 헤드라인 하나 — 항목은 그 칸의 키(항목 별칭)다
 *
 * @param title 제목
 * @param note 짧은 코멘트 — 없으면 null
 */
public record HeadlineDraft(String title, @Nullable String note) {

	public HeadlineDraft {
		Objects.requireNonNull(title, "title");
	}

}
