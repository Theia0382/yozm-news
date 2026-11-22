package net.yozmnews.report.domain.output;

import static net.yozmnews.report.ReportFixtures.event;
import static net.yozmnews.report.ReportFixtures.input;
import static net.yozmnews.report.ReportFixtures.section;
import static net.yozmnews.report.ReportFixtures.storyline;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.alias.ReportAliases;
import org.junit.jupiter.api.Test;

/**
 * 별칭 대조 — 입력 순서, 모르는 별칭 · 빈 별칭 버림, 같은 대상 한 번, 다른 갈래의 별칭, 빠진 갈래.
 * <p>
 * 지키는 규칙: report-R7
 */
class ReportCitationCheckTests {

	private static final ReportAliases ALIASES = ReportAliases.of(input(
			section(ReportSectionKind.STORYLINES, storyline(31), event(902)),
			section(ReportSectionKind.COVERAGE, event(903)),
			section(ReportSectionKind.INTERESTS)));

	@Test
	void keepsKnownAliasesInOrderAndCountsDroppedOnes() {
		CitedReport cited = ReportCitationCheck.check(new ReportDraft("개요", Map.of(
				ReportSectionKind.COVERAGE, new SectionDraft("보도량", "본문 둘", List.of("E2", "S1", "Z9", "", "E2")),
				ReportSectionKind.STORYLINES, new SectionDraft("흐름", "본문 하나", List.of("E1", "S1")),
				ReportSectionKind.INTERESTS, new SectionDraft("관심", "본문 셋", List.of()))), ALIASES);

		assertThat(cited.overview()).isEqualTo("개요");
		assertThat(cited.sections()).containsExactly(
				new CitedSection(ReportSectionKind.STORYLINES, "흐름", "본문 하나", List.of(event(902), storyline(31))),
				new CitedSection(ReportSectionKind.COVERAGE, "보도량", "본문 둘", List.of(event(903), storyline(31))),
				new CitedSection(ReportSectionKind.INTERESTS, "관심", "본문 셋", List.of()));
		assertThat(cited.unknownRefs()).isEqualTo(2);
		assertThat(cited.missingSections()).isZero();
	}

	@Test
	void aSectionWithoutAFieldIsMissingAndAnOutputWithOnlyKnownAliasesDropsNothing() {
		CitedReport cited = ReportCitationCheck.check(new ReportDraft("개요",
				Map.of(ReportSectionKind.COVERAGE, new SectionDraft("보도량", "본문", List.of("E2")))), ALIASES);

		assertThat(cited.sections()).containsExactly(
				new CitedSection(ReportSectionKind.COVERAGE, "보도량", "본문", List.of(event(903))));
		assertThat(cited.missingSections()).isEqualTo(2);
		assertThat(cited.unknownRefs()).isZero();
	}

}
