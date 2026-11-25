package net.yozmnews.reader.adapter.in.web;

import java.util.Objects;

import net.yozmnews.report.InterestShift;
import net.yozmnews.report.ReportSectionStats;
import org.jspecify.annotations.Nullable;

/**
 * 사용자 스토리라인 하나의 관심 — 기간 시작 · 끝의 관심 값 · 변화. docs/specs/reader.md §5 reader-R16.
 *
 * @param start 기간 시작의 관심 값 — 신호가 없으면 null
 * @param end 기간 끝의 관심 값 — 신호가 없으면 null
 */
record ReportUserStorylineLevelResponse(ReportStorylineResponse storyline, @Nullable Double start, @Nullable Double end,
		InterestShift shift) {

	ReportUserStorylineLevelResponse {
		Objects.requireNonNull(storyline, "storyline");
		Objects.requireNonNull(shift, "shift");
	}

	static ReportUserStorylineLevelResponse of(ReportSectionStats.UserStorylineLevel level, ReportNames.Named named) {
		return new ReportUserStorylineLevelResponse(Objects.requireNonNull(named.storylines().get(level.storyline()), "storyline"),
				level.start(), level.end(), level.shift());
	}

}
