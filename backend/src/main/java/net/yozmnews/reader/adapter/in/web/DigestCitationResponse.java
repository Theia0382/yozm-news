package net.yozmnews.reader.adapter.in.web;

import net.yozmnews.digest.SelectionReason;
import org.jspecify.annotations.Nullable;

/**
 * 다이제스트가 인용한 기사 한 줄 — 기사 정보는 언제나 {@code article} 의 값이다(reader-R7). docs/specs/reader.md §5 · reader-R9
 *
 * @param articleId 기사 ID
 * @param article 제목 · 출처 이름 · 발행 시각 · 원문 링크
 * @param note 추가 헤드라인의 코멘트 — 없으면 null
 * @param reason 선정 이유
 */
record DigestCitationResponse(long articleId, NewsArticleResponse article, @Nullable String note,
		SelectionReason reason) {
}
