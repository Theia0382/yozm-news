package net.yozmnews.report;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 보이는 섹션 하나 · docs/specs/report.md §3 · report-R11 · report-R12 · report-R25
 *
 * @param title 갈래 섹션의 제목 — 개요는 {@code null}
 * @param citations 지금 가리키는 인용 — 합쳐진 것은 남는 것, 지운 것은 빠진다. 개요는 빈 목록
 * @param stats 그 갈래의 숫자 — 개요이거나, 스냅숏에 그 갈래의 칸이 없거나, 스냅숏을 읽지 못하면 {@code null}
 */
public record ReportSectionView(ReportSectionKind kind, @Nullable String title, String body,
		List<ReportCitation> citations, @Nullable ReportSectionStats stats) {

	/**
	 * @throws IllegalArgumentException 숫자가 섹션 종류의 숫자가 아닐 때 — 흐름은 {@link ReportSectionStats.Flows}, 보도량은
	 * {@link ReportSectionStats.Coverage}, 관심은 {@link ReportSectionStats.Interests}, 개요는 없음
	 */
	public ReportSectionView {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(body, "body");
		citations = List.copyOf(citations);
		if (stats != null && !matches(kind, stats)) {
			throw new IllegalArgumentException("섹션 종류와 숫자가 맞지 않는다: " + kind + " · " + stats.getClass().getSimpleName());
		}
	}

	private static boolean matches(ReportSectionKind kind, ReportSectionStats stats) {
		return switch (kind) {
			case OVERVIEW -> false;
			case STORYLINES -> stats instanceof ReportSectionStats.Flows;
			case COVERAGE -> stats instanceof ReportSectionStats.Coverage;
			case INTERESTS -> stats instanceof ReportSectionStats.Interests;
		};
	}

}
