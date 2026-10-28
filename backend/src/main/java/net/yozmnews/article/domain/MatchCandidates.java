package net.yozmnews.article.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import org.jspecify.annotations.Nullable;

/**
 * 같은 기사 판정의 후보 — 저장소가 모아 오고, 판정은 {@link ArticleMatching} 이 한다.
 *
 * @param byArticleUrl 정규 URL 해시가 항목의 것과 같은 기사
 * @param byEntryUrl 엔트리에 남은 항목 URL 해시가 이 항목의 정규 URL 해시와 같은 엔트리의 기사 — 여럿이면 id 가 가장 작은 것
 * @param byGuid 같은 피드에서 guid 해시가 같은 엔트리의 기사와 그 기사의 제목 비교 키 해시 — 항목에 guid 가 없으면 비어 있다
 * @param byTitle 같은 소스에서 제목 비교 키 해시가 같고 발행 시각이 시간 창 안인 기사 — 키가 비면 비어 있다
 */
public record MatchCandidates(@Nullable ArticleId byArticleUrl, @Nullable ArticleId byEntryUrl,
		List<GuidCandidate> byGuid, List<TitleCandidate> byTitle) {

	public MatchCandidates {
		byGuid = List.copyOf(byGuid);
		byTitle = List.copyOf(byTitle);
	}

	/**
	 * @param articleId 같은 guid 의 엔트리가 붙은 기사
	 * @param titleKeyHash 그 기사의 제목 비교 키 해시
	 */
	public record GuidCandidate(ArticleId articleId, KeyHash titleKeyHash) {

		public GuidCandidate {
			Objects.requireNonNull(articleId, "articleId");
			Objects.requireNonNull(titleKeyHash, "titleKeyHash");
		}

	}

	/**
	 * @param articleId 기사
	 * @param publishedAt 기사의 발행 시각
	 * @param seenInFeed 항목을 받은 피드에 이 기사의 엔트리가 이미 있는지
	 */
	public record TitleCandidate(ArticleId articleId, Instant publishedAt, boolean seenInFeed) {

		public TitleCandidate {
			Objects.requireNonNull(articleId, "articleId");
			Objects.requireNonNull(publishedAt, "publishedAt");
		}

	}

}
