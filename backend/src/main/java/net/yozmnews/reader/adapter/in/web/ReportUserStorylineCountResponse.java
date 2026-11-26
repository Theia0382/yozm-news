package net.yozmnews.reader.adapter.in.web;

import java.util.Objects;

import net.yozmnews.report.ReportSectionStats;
import net.yozmnews.report.ReportTrend;
import net.yozmnews.storyline.StorylineState;
import org.jspecify.annotations.Nullable;

/**
 * 추적한 사용자 스토리라인 하나의 보도량 — 이번 · 이은 직전 값 · 판정 · 생성 때 상태. docs/specs/reader.md §5 reader-R16.
 *
 * @param previous 이은 직전 값 — 없으면 null(월간은 언제나 null)
 * @param trackedInPeriod 기간 안에 추적을 더했는가
 * @param state 생성 때 헤드의 상태 — 상태를 남기지 않은 옛 리포트면 null
 */
record ReportUserStorylineCountResponse(ReportStorylineResponse storyline, long articles, @Nullable Long previous, ReportTrend trend,
		boolean trackedInPeriod, @Nullable StorylineState state) {

	ReportUserStorylineCountResponse {
		Objects.requireNonNull(storyline, "storyline");
		Objects.requireNonNull(trend, "trend");
	}

	static ReportUserStorylineCountResponse of(ReportSectionStats.UserStorylineCount count, ReportNames.Named named) {
		return new ReportUserStorylineCountResponse(Objects.requireNonNull(named.storylines().get(count.storyline()), "storyline"),
				count.articles(), count.previous(), count.trend(), count.trackedInPeriod(), count.state());
	}

}
