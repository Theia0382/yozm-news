package net.yozmnews.digest.domain.output;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 읽은 헤드라인 하나 — 항목은 원소의 별칭({@link DigestOutputSchema#REF})이다
 *
 * @param title 제목
 * @param note 짧은 코멘트 — 없으면 null
 */
public record HeadlineDraft(String title, @Nullable String note) {

	public HeadlineDraft {
		Objects.requireNonNull(title, "title");
	}

}
