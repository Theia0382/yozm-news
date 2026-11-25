package net.yozmnews.reader.adapter.in.web;

import java.util.List;
import java.util.Objects;

import net.yozmnews.report.ReportView;
import org.jspecify.annotations.Nullable;

/**
 * 변화 리포트 한 편 — 요약 · 비교한 직전 리포트 · 숨긴 섹션 수 · 사용량 · 보이는 섹션 · 남은 다시 만들기 수. docs/specs/reader.md §5 reader-R16.
 *
 * @param previous 생성 때 숫자를 이은 직전 리포트 — 없었거나 그새 없어졌으면 null
 * @param hiddenSections 인용한 흐름 · 사건이 사라져 숨긴 갈래 섹션 수
 * @param usage 공급자 · 모델 · 토큰 — 시작 전이면 null
 * @param sections 보이는 섹션 — 위치 순. {@code READY} 가 아니면 빈 목록
 * @param regenerationsLeft 실패한 리포트를 더 다시 만들 수 있는 횟수 — 실패일 때만, 아니면 null
 */
record ReportDetailResponse(ReportSummaryResponse report, @Nullable ReportSummaryResponse previous,
		int hiddenSections, @Nullable ReportUsageResponse usage, List<ReportSectionResponse> sections,
		@Nullable Integer regenerationsLeft) {

	ReportDetailResponse {
		Objects.requireNonNull(report, "report");
		sections = List.copyOf(sections);
	}

	static ReportDetailResponse of(ReportView view, ReportFlows.Named named) {
		return new ReportDetailResponse(ReportSummaryResponse.of(view.summary()),
				(view.previous() == null) ? null : ReportSummaryResponse.of(view.previous()), view.hiddenSections(),
				ReportUsageResponse.of(view),
				view.sections().stream().map(section -> ReportSectionResponse.of(section, named)).toList(),
				view.regenerationsLeft());
	}

}
