package net.yozmnews.reader.adapter.in.web;

import java.util.Objects;

import net.yozmnews.report.ReportCitation;
import org.jspecify.annotations.Nullable;

/**
 * 변화 리포트 섹션의 인용 하나 — 스토리라인 또는 사건. docs/specs/reader.md §5 reader-R16.
 *
 * @param storyline 스토리라인 인용의 표시 — 사건 인용이면 null
 * @param eventId 사건 인용의 사건 ID — 스토리라인 인용이면 null
 * @param article 사건 인용의 대표 기사 — 스토리라인 인용이거나 대표 기사를 찾지 못하면 null
 */
record ReportCitationResponse(Kind kind, @Nullable ReportStorylineResponse storyline, @Nullable Long eventId,
		@Nullable NewsArticleResponse article) {

	/** 인용 대상의 종류 */
	enum Kind {
		/** 스토리라인 */
		STORYLINE,
		/** 사건 */
		EVENT
	}

	ReportCitationResponse {
		Objects.requireNonNull(kind, "kind");
	}

	static ReportCitationResponse of(ReportCitation citation, ReportNames.Named named) {
		return switch (citation) {
			case ReportCitation.Storyline storyline ->
				new ReportCitationResponse(Kind.STORYLINE, named.storylines().get(storyline.id()), null, null);
			case ReportCitation.Event event ->
				new ReportCitationResponse(Kind.EVENT, null, event.id().value(), named.events().get(event.id()));
		};
	}

}
