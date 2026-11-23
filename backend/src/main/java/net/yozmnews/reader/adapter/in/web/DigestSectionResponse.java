package net.yozmnews.reader.adapter.in.web;

import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.DigestSectionView;
import net.yozmnews.digest.SectionKind;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.event.EventId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.UserStorylineSummaryRef;
import org.jspecify.annotations.Nullable;

/**
 * 다이제스트의 섹션 하나 — 개요 또는 항목 하나(주요 뉴스 · 헤드라인). docs/specs/reader.md §5 · reader-R9
 *
 * @param kind 섹션 종류
 * @param title 항목의 제목 — 개요면 null
 * @param body 개요 · 주요 뉴스의 요약 · 헤드라인의 코멘트 — 코멘트가 없는 헤드라인이면 null
 * @param reason 항목의 선정 이유(배지 하나) — 개요면 null
 * @param citations 인용(출처 기사) — 위치 순. 그새 지운 기사의 인용은 빠진다
 * @param eventIds 항목의 사건 — 위치 순, 첫 사건이 항목 키(좋아요 / 싫어요를 누르는 손잡이 — docs/specs/feedback.md). 개요는 비어 있다
 * @param storylineId 항목을 묶은 흐름(스토리라인 포함 나무의 지금 꼭대기) — 흐름 추적의 대상. 개요 · 흐름 없는 항목이면 null
 * @param userStorylines 그 흐름을 추적 대상으로 가진 정리된 사용자 스토리라인 모두 — 제목과 링크, 사용자 스토리라인 ID 순. 갈라지기 전 흐름이면
 *        그 뒤 갈래의 것 여럿이다. 없으면 빈 목록
 */
record DigestSectionResponse(SectionKind kind, @Nullable String title, @Nullable String body,
		@Nullable SelectionReason reason, List<DigestCitationResponse> citations, List<Long> eventIds,
		@Nullable Long storylineId, List<UserStorylineResponses.RefResponse> userStorylines) {

	DigestSectionResponse {
		userStorylines = List.copyOf(userStorylines);
	}

	static DigestSectionResponse of(DigestSectionView section, Map<ArticleId, NewsArticleResponse> articles,
			Map<StorylineId, List<UserStorylineSummaryRef>> userStorylines) {
		List<UserStorylineSummaryRef> refs = (section.storyline() == null) ? List.of()
				: userStorylines.getOrDefault(section.storyline(), List.of());
		return new DigestSectionResponse(section.kind(), section.title(), section.body(), section.reason(),
				section.citations()
					.stream()
					.filter(citation -> articles.containsKey(citation.article()))
					.map(citation -> new DigestCitationResponse(citation.article().value(),
							articles.get(citation.article())))
					.toList(),
				section.events().stream().map(EventId::value).toList(),
				(section.storyline() == null) ? null : section.storyline().value(),
				refs.stream().map(UserStorylineResponses.RefResponse::of).toList());
	}

}
