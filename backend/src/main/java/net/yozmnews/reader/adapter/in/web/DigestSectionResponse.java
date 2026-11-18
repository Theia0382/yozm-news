package net.yozmnews.reader.adapter.in.web;

import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.DigestSectionView;
import net.yozmnews.digest.SectionKind;
import org.jspecify.annotations.Nullable;

/**
 * 다이제스트의 섹션 하나 — 개요 · 주요 뉴스 · 추가 헤드라인 묶음. docs/specs/reader.md §5 · reader-R9
 *
 * @param kind 섹션 종류
 * @param title 주요 뉴스의 제목 — 다른 종류면 null
 * @param body 개요 · 주요 뉴스의 본문 — 추가 헤드라인 묶음이면 null
 * @param citations 인용 — 위치 순. 그새 지운 기사의 인용은 빠진다
 */
record DigestSectionResponse(SectionKind kind, @Nullable String title, @Nullable String body,
		List<DigestCitationResponse> citations) {

	static DigestSectionResponse of(DigestSectionView section, Map<ArticleId, NewsArticleResponse> articles) {
		return new DigestSectionResponse(section.kind(), section.title(), section.body(), section.citations()
			.stream()
			.filter(citation -> articles.containsKey(citation.article()))
			.map(citation -> new DigestCitationResponse(citation.article().value(), articles.get(citation.article()),
					citation.note(), citation.reason()))
			.toList());
	}

}
