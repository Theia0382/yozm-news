package net.yozmnews.article.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

import net.yozmnews.article.domain.MatchCandidates.GuidCandidate;
import net.yozmnews.article.domain.MatchCandidates.TitleCandidate;

/**
 * 같은 기사 판정 규칙 — 정본. 잘못 합치면 뒤 기사를 잃고 되살릴 수 없지만, 잘못 가르면 생긴 중복은 사건 묶기가 받친다. 그래서
 * guid · 제목은 정규 URL 을 보조하는 키로만 쓴다. 판정 순서:
 * <ol>
 * <li>정규 URL 이 기사의 URL 과 같다 → {@link MatchKind#URL}</li>
 * <li>정규 URL 이 엔트리에 남은 항목 URL 과 같다 → {@link MatchKind#URL}. 제목으로 붙은 항목은 URL 이 엔트리에만 남으므로,
 * 다시 처리해도 같은 기사에 걸린다</li>
 * <li>같은 피드에 같은 guid 의 기사가 있고 제목 비교 키도 같다 → {@link MatchKind#GUID}. 여럿이면 id 가 가장 작은 것</li>
 * <li>같은 소스에서 이 피드에서는 본 적 없는 기사의 제목 비교 키가 같고 발행 시각 차가 {@link #TITLE_WINDOW} 이하다 →
 * {@link MatchKind#TITLE}. 여럿이면 발행 시각 차가 가장 작은 것, 같으면 id 가 가장 작은 것. 같은 피드에 날마다 같은 제목으로
 * 나오는 정형 기사를 합치지 않는다</li>
 * <li>그 밖에는 새 기사</li>
 * </ol>
 * 제목 비교 키가 비면 3 · 4 를 건너뛴다.
 */
public final class ArticleMatching {

	/** L2 시간 창 — 발행 시각 차가 이 이하(경계 포함)면 같은 기사 후보 */
	public static final Duration TITLE_WINDOW = Duration.ofHours(48);

	private ArticleMatching() {
	}

	/** 제목 후보를 찾을 발행 시각 범위 — 저장소는 이 범위로 후보를 모은다 */
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
