package net.yozmnews.admin.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;

import net.yozmnews.storyline.StorylineState;
import net.yozmnews.storyline.StorylineSummary;
import net.yozmnews.storyline.StorylineView;
import org.jspecify.annotations.Nullable;

/**
 * 스토리라인 목록의 행 — 스토리라인 값 · 기간 집계 · 큰 여부 · 대표 기사 · docs/specs/admin.md §5 admin-R7.
 *
 * @param parentId 이 스토리라인을 포함하는 상위 — 없으면 null
 * @param eventCount 닫은 사건 집합의 사건 수(하위의 사건 포함)
 * @param articleCount 닫은 사건 집합의 기사 수 — 기간과 무관하다
 * @param periodArticles 고른 기간 안 기사 수
 * @param periodSources 고른 기간 안 서로 다른 소스 수
 * @param importance 고른 기간 안 중요도 평균 — 평가가 없으면 null
 * @param big 고른 기간 · 지금 기준으로 큰 스토리라인인가
 * @param representativeArticleId 대표 사건의 기사 전체에서 고른 대표 기사 — 대표 사건이 그새 합쳐졌거나 지워졌으면 null
 */
record StorylineRowResponse(long id, StorylineState state, @Nullable Long parentId, int eventCount, int articleCount,
		Instant firstArticleAt, Instant lastArticleAt, int periodArticles, int periodSources,
		@Nullable BigDecimal importance, boolean big, @Nullable Long representativeArticleId,
		@Nullable ArticleSummaryResponse representative) {

	static StorylineRowResponse of(StorylineSummary summary, AdminRepresentatives.Representative representative) {
		StorylineView view = summary.storyline();
		return new StorylineRowResponse(view.id().value(), view.state(),
				(view.parent() == null) ? null : view.parent().value(), view.eventCount(), view.articleCount(),
				view.firstArticleAt(), view.lastArticleAt(), summary.tally().articles(), summary.tally().sources(),
				summary.tally().importance(), summary.big(), representative.articleId(), representative.response());
	}

}
