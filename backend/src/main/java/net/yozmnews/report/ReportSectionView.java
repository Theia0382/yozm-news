package net.yozmnews.report;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 보이는 섹션 하나 · docs/specs/report.md §3 · report-R11 · report-R12
 *
 * @param title 갈래 섹션의 제목 — 개요는 {@code null}
 * @param citations 지금 가리키는 인용 — 합쳐진 것은 남는 것, 지운 것은 빠진다. 개요는 빈 목록
 */
public record ReportSectionView(ReportSectionKind kind, @Nullable String title, String body,
		List<ReportCitation> citations) {

	public ReportSectionView {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(body, "body");
		citations = List.copyOf(citations);
	}

}
