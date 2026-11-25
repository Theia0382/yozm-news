package net.yozmnews.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * 섹션 값 · 요약 · 한 편의 불변식 — 섹션 종류와 숫자의 짝, 기간 날짜의 순서, 숨긴 섹션 수의 하한. 지키는 규칙: report-R25 · report-R26
 */
class ReportSectionViewTests {

	private static final ReportSectionStats FLOWS = new ReportSectionStats.Flows(List.of());

	private static final ReportSectionStats COVERAGE = new ReportSectionStats.Coverage(List.of());

	private static final ReportSectionStats INTERESTS = new ReportSectionStats.Interests(List.of(), List.of());

	@Test
	void eachKindSectionTakesOnlyTheNumbersOfItsKind() {
		assertThatNoException().isThrownBy(() -> section(ReportSectionKind.STORYLINES, FLOWS));
		assertThatNoException().isThrownBy(() -> section(ReportSectionKind.COVERAGE, COVERAGE));
		assertThatNoException().isThrownBy(() -> section(ReportSectionKind.INTERESTS, INTERESTS));
		assertThatNoException().isThrownBy(() -> section(ReportSectionKind.COVERAGE, null));

		assertThatIllegalArgumentException().isThrownBy(() -> section(ReportSectionKind.COVERAGE, FLOWS));
		assertThatIllegalArgumentException().isThrownBy(() -> section(ReportSectionKind.STORYLINES, INTERESTS));
		assertThatIllegalArgumentException().isThrownBy(() -> section(ReportSectionKind.INTERESTS, COVERAGE));
	}

	@Test
	void theOverviewHasNoNumbers() {
		assertThatNoException().isThrownBy(() -> section(ReportSectionKind.OVERVIEW, null));
		assertThatIllegalArgumentException().isThrownBy(() -> section(ReportSectionKind.OVERVIEW, COVERAGE));
	}

	@Test
	void theLastDayIsNotBeforeTheFirstDay() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ReportSummary(new ReportId(1), ReportPeriodKind.WEEK,
				Instant.parse("2026-11-16T00:00:00Z"), Instant.parse("2026-11-23T00:00:00Z"), LocalDate.parse("2026-11-16"),
				LocalDate.parse("2026-11-15"), ReportStatus.SCHEDULED, null, Instant.parse("2026-11-23T00:00:00Z"), null));
		assertThatNoException().isThrownBy(() -> new ReportSummary(new ReportId(1), ReportPeriodKind.WEEK,
				Instant.parse("2026-11-16T00:00:00Z"), Instant.parse("2026-11-17T00:00:00Z"), LocalDate.parse("2026-11-16"),
				LocalDate.parse("2026-11-16"), ReportStatus.SCHEDULED, null, Instant.parse("2026-11-23T00:00:00Z"), null));
	}

	@Test
	void theHiddenSectionCountIsNotNegative() {
		ReportSummary summary = new ReportSummary(new ReportId(1), ReportPeriodKind.WEEK,
				Instant.parse("2026-11-16T00:00:00Z"), Instant.parse("2026-11-23T00:00:00Z"), LocalDate.parse("2026-11-16"),
				LocalDate.parse("2026-11-22"), ReportStatus.READY, null, Instant.parse("2026-11-23T00:00:00Z"),
				Instant.parse("2026-11-23T00:01:00Z"));

		assertThat(new ReportView(summary, null, null, null, null, null, 0, List.of(), null).hiddenSections()).isZero();
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ReportView(summary, null, null, null, null, null, -1, List.of(), null));
		assertThat(new ReportView(summary, null, null, null, null, null, 0, List.of(), 0).regenerationsLeft()).isZero();
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ReportView(summary, null, null, null, null, null, 0, List.of(), -1));
	}

	private static ReportSectionView section(ReportSectionKind kind, @Nullable ReportSectionStats stats) {
		return new ReportSectionView(kind, null, "본문", List.of(), stats);
	}

}
