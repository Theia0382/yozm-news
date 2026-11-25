package net.yozmnews.reader.adapter.in.web;

import java.util.List;
import java.util.Objects;

import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.ReportSectionStats;
import net.yozmnews.report.ReportSectionView;
import org.jspecify.annotations.Nullable;

/**
 * 변화 리포트의 보이는 섹션 하나 — 제목 · 본문 · 인용과 그 갈래의 숫자. docs/specs/reader.md §5 reader-R16.
 *
 * @param title 갈래 섹션의 제목 — 개요는 null
 * @param citations 지금 가리키는 인용 — 개요는 빈 목록
 * @param userStorylines 추적한 사용자 스토리라인 섹션의 보도량 — 그 밖의 섹션이거나 숫자가 없으면 null
 * @param topics 구독 주제 섹션의 보도량 — 그 밖의 섹션이거나 숫자가 없으면 null
 * @param interests 관심 섹션의 변화 — 그 밖의 섹션이거나 숫자가 없으면 null
 */
record ReportSectionResponse(ReportSectionKind kind, @Nullable String title, String body,
		List<ReportCitationResponse> citations, @Nullable List<ReportUserStorylineCountResponse> userStorylines,
		@Nullable List<ReportTopicCountResponse> topics, @Nullable ReportInterestsResponse interests) {

	ReportSectionResponse {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(body, "body");
		citations = List.copyOf(citations);
	}

	static ReportSectionResponse of(ReportSectionView section, ReportNames.Named named) {
		List<ReportCitationResponse> citations = section.citations()
			.stream()
			.map(citation -> ReportCitationResponse.of(citation, named))
			.toList();
		ReportSectionStats stats = section.stats();
		if (stats == null) {
			return new ReportSectionResponse(section.kind(), section.title(), section.body(), citations, null, null,
					null);
		}
		return switch (stats) {
			case ReportSectionStats.UserStorylines counts -> new ReportSectionResponse(section.kind(), section.title(),
					section.body(), citations,
					counts.userStorylines().stream().map(count -> ReportUserStorylineCountResponse.of(count, named)).toList(), null, null);
			case ReportSectionStats.Coverage coverage -> new ReportSectionResponse(section.kind(), section.title(),
					section.body(), citations, null,
					coverage.topics().stream().map(ReportTopicCountResponse::of).toList(), null);
			case ReportSectionStats.Interests interests -> new ReportSectionResponse(section.kind(), section.title(),
					section.body(), citations, null, null, ReportInterestsResponse.of(interests, named));
		};
	}

}
