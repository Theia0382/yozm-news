package net.yozmnews.reader.adapter.in.web;

import java.util.Objects;

import net.yozmnews.report.InterestShift;
import net.yozmnews.report.ReportSectionStats;
import org.jspecify.annotations.Nullable;

/**
 * 주제 하나의 관심 — 생성 때의 주제 이름 · 기간 시작 · 끝의 관심 값 · 변화. docs/specs/reader.md §5 reader-R16.
 *
 * @param start 기간 시작의 관심 값 — 신호가 없으면 null
 * @param end 기간 끝의 관심 값 — 신호가 없으면 null
 */
record ReportTopicLevelResponse(String name, @Nullable Double start, @Nullable Double end, InterestShift shift) {

	ReportTopicLevelResponse {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(shift, "shift");
	}

	static ReportTopicLevelResponse of(ReportSectionStats.TopicLevel level) {
		return new ReportTopicLevelResponse(level.name(), level.start(), level.end(), level.shift());
	}

}
