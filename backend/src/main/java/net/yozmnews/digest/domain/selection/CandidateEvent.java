package net.yozmnews.digest.domain.selection;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import net.yozmnews.storyline.StorylineId;

/**
 * 후보 사건 하나 — 사건의 공유 값과, 그 사용자에게 그 기간에 든 기사 · docs/specs/digest.md §5 · digest-R17 · digest-R45.
 *
 * @param facts 사건의 공유 값
 * @param tops 사건이 걸친 포함 나무의 꼭대기({@code storyline} 의 꼭대기 조회 순서) — 소속이 없으면 빈 목록
 * @param articles 그 사건의 기간에 든 기사 가운데 사용자의 기사 제외를 지난 기사 — 비지 않음, 발행 시각 · 기사 ID 순으로 둔다
 */
public record CandidateEvent(EventFacts facts, List<StorylineId> tops, List<ArticleFacts> articles) {

	private static final Comparator<ArticleFacts> PUBLISHED = Comparator.comparing(ArticleFacts::publishedAt)
		.thenComparingLong(article -> article.id().value());

	/**
	 * @throws IllegalArgumentException 기사가 없을 때
	 */
	public CandidateEvent {
		Objects.requireNonNull(facts, "facts");
		tops = List.copyOf(Objects.requireNonNull(tops, "tops"));
		Objects.requireNonNull(articles, "articles");
		if (articles.isEmpty()) {
			throw new IllegalArgumentException("기사가 없는 후보 사건이다: " + facts.id().value());
		}
		articles = articles.stream().sorted(PUBLISHED).toList();
	}

}
