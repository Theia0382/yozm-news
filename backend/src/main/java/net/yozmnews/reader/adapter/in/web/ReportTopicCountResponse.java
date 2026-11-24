package net.yozmnews.reader.adapter.in.web;

import java.util.Objects;

import net.yozmnews.report.ReportSectionStats;
import net.yozmnews.report.ReportTrend;
import org.jspecify.annotations.Nullable;

/**
 * 구독 주제 하나의 보도량 — 생성 때의 주제 이름 · 이번 · 이은 직전 값 · 판정. docs/specs/reader.md §5 reader-R16.
 *
 * @param previous 이은 직전 값 — 없으면 null
 */
record ReportTopicCountResponse(String name, long articles, @Nullable Long previous, ReportTrend trend) {

	ReportTopicCountResponse {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(trend, "trend");
	}

	static ReportTopicCountResponse of(ReportSectionStats.TopicCount count) {
		return new ReportTopicCountResponse(count.name(), count.articles(), count.previous(), count.trend());
	}

}
