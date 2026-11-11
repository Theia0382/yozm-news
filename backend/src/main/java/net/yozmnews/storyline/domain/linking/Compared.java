package net.yozmnews.storyline.domain.linking;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 판정할 사건과 견줄 것 하나 — 스토리라인 표현이나 이웃 사건 · docs/specs/storyline.md §4 · storyline-R8.
 *
 * @param id 스토리라인 ID 또는 사건 ID
 * @param cosine 판정할 사건 중심과의 코사인 유사도 — 셀 수 없으면 NaN
 * @param keyTerms 상위 핵심 말
 */
public record Compared(long id, double cosine, Instant firstArticleAt, Instant lastArticleAt, List<String> keyTerms) {

	public Compared {
		Objects.requireNonNull(firstArticleAt, "firstArticleAt");
		Objects.requireNonNull(lastArticleAt, "lastArticleAt");
		keyTerms = List.copyOf(keyTerms);
	}

}
