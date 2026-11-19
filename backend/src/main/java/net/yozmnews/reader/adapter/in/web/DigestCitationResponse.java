package net.yozmnews.reader.adapter.in.web;

/**
 * 다이제스트가 인용한 기사(출처 기사) 하나 — 기사 정보는 언제나 {@code article} 의 값이다(reader-R7). docs/specs/reader.md §5 · reader-R9
 *
 * @param articleId 기사 ID
 * @param article 제목 · 출처 이름 · 발행 시각 · 원문 링크
 */
record DigestCitationResponse(long articleId, NewsArticleResponse article) {
}
