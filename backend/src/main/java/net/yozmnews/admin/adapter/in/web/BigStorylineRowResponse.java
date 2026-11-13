package net.yozmnews.admin.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;

import net.yozmnews.storyline.BigStoryline;
import net.yozmnews.storyline.StorylineState;
import org.jspecify.annotations.Nullable;

/**
 * 기간의 큰 스토리라인 하나 — 스토리라인 값, 기간 집계, 대표 사건의 대표 기사 · docs/specs/admin.md §5 admin-R7.
 *
 * @param eventCount 구성 사건 수 — 기간과 무관한 스토리라인 전체
 * @param articleCount 스토리라인 전체의 기사 수
 * @param intensity 기간의 보도 강도 — 큰 판정이 기준과 견준 값
 * @param periodArticles 기간 안 기사 수
 * @param periodSources 기간 안 기사의 서로 다른 소스 수
 * @param importance 기간 안 중요도 평균 — 중요도가 있는 기사가 없으면 null
 * @param representativeArticleId 대표 사건의 기사 전체에서 고른 대표 기사 — 대표 사건이 그새 합쳐졌거나 지워졌으면 null
 * @param representative 대표 기사의 제목 · 출처 — 대표가 없거나 그새 지웠으면 null
 */
record BigStorylineRowResponse(long id, StorylineState state, int eventCount, int articleCount, Instant firstArticleAt,
		Instant lastArticleAt, BigDecimal intensity, int periodArticles, int periodSources, @Nullable BigDecimal importance,
		@Nullable Long representativeArticleId, @Nullable ArticleSummaryResponse representative) {

	static BigStorylineRowResponse of(BigStoryline big, AdminRepresentatives.Representative representative) {
		return new BigStorylineRowResponse(big.storyline().id().value(), big.storyline().state(),
				big.storyline().eventCount(), big.storyline().articleCount(), big.storyline().firstArticleAt(),
				big.storyline().lastArticleAt(), big.intensity(), big.tally().articles(), big.tally().sources(),
				big.tally().importance(), representative.articleId(), representative.response());
	}

}
