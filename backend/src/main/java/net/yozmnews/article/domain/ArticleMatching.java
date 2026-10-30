package net.yozmnews.article.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

import net.yozmnews.article.domain.MatchCandidates.GuidCandidate;
import net.yozmnews.article.domain.MatchCandidates.TitleCandidate;

/**
 * 같은 기사 판정 — 정본 · docs/specs/article.md §4 · article-R3 ~ article-R6
 */
public final class ArticleMatching {

	/** 제목 판정의 시간 창 */
	public static final Duration TITLE_WINDOW = Duration.ofHours(48);

	private ArticleMatching() {
	}

	/** 발행 시각 앞뒤로 시간 창만큼의 범위를 돌려준다 — 저장소가 제목 후보를 이 범위에서 모은다 */
	public static TimeRange titleWindowAround(Instant publishedAt) {
		return new TimeRange(publishedAt.minus(TITLE_WINDOW), publishedAt.plus(TITLE_WINDOW));
	}

	public static MatchDecision decide(IncomingItem item, MatchCandidates candidates) {
		if (candidates.byArticleUrl() != null) {
			return new MatchDecision.Existing(candidates.byArticleUrl(), MatchKind.URL);
		}
		if (candidates.byEntryUrl() != null) {
			return new MatchDecision.Existing(candidates.byEntryUrl(), MatchKind.URL);
		}
		if (item.titleKey().isEmpty()) {
			return new MatchDecision.New(false);
		}
		KeyHash titleKeyHash = item.titleKey().hash();
		Optional<GuidCandidate> byGuid = candidates.byGuid()
			.stream()
			.filter((candidate) -> candidate.titleKeyHash().equals(titleKeyHash))
			.min(Comparator.comparingLong((candidate) -> candidate.articleId().value()));
		if (byGuid.isPresent()) {
			return new MatchDecision.Existing(byGuid.get().articleId(), MatchKind.GUID);
		}
		Instant publishedAt = item.article().publishedAt();
		Optional<TitleCandidate> byTitle = candidates.byTitle()
			.stream()
			.filter((candidate) -> !candidate.seenInFeed())
			.filter((candidate) -> distance(candidate, publishedAt).compareTo(TITLE_WINDOW) <= 0)
			.min(Comparator.comparing((TitleCandidate candidate) -> distance(candidate, publishedAt))
				.thenComparingLong((candidate) -> candidate.articleId().value()));
		if (byTitle.isPresent()) {
			return new MatchDecision.Existing(byTitle.get().articleId(), MatchKind.TITLE);
		}
		return new MatchDecision.New(!candidates.byGuid().isEmpty());
	}

	private static Duration distance(TitleCandidate candidate, Instant publishedAt) {
		return Duration.between(candidate.publishedAt(), publishedAt).abs();
	}

}
