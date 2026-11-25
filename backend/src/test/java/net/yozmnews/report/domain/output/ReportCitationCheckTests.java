package net.yozmnews.report.domain.output;

import static net.yozmnews.report.ReportFixtures.coverage;
import static net.yozmnews.report.ReportFixtures.event;
import static net.yozmnews.report.ReportFixtures.input;
import static net.yozmnews.report.ReportFixtures.interests;
import static net.yozmnews.report.ReportFixtures.storyline;
import static net.yozmnews.report.ReportFixtures.userStoryline;
import static net.yozmnews.report.ReportFixtures.userStorylines;
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
			userStorylines(userStoryline(31, 902), userStoryline(44, 903)), coverage(), interests()));

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

	@Test
	void namesTheMissingSectionsInInputOrder() {
		ReportDraft onlyCoverage = new ReportDraft("개요",
				Map.of(ReportSectionKind.COVERAGE, new SectionDraft("보도량", "본문", List.of())));

		assertThat(ReportCitationCheck.missing(onlyCoverage, ALIASES))
			.containsExactly(ReportSectionKind.STORYLINES, ReportSectionKind.INTERESTS);
		assertThat(ReportCitationCheck.missing(new ReportDraft("개요", Map.of()), ReportAliases.of(input())))
			.isEmpty();
	}

	@Test
	void findsAliasesOfThisRequestLeftInTheOverviewTitlesAndBodies() {
		ReportDraft draft = new ReportDraft("S1의 보도가 이어졌다", Map.of(
				ReportSectionKind.STORYLINES, new SectionDraft("E2 와 흐름", "GS1 · S10 · S1x 는 별칭이 아니다", List.of("S1", "S2")),
				ReportSectionKind.COVERAGE, new SectionDraft("보도량", "(E1)이 늘었다", List.of())));

		// 별칭을 매긴 순서(S1 · S2 · E1 · E2 가운데 글에 있는 것), refs 에만 있는 S2 는 세지 않는다
		assertThat(ReportCitationCheck.leftAliases(draft, ALIASES)).containsExactly("S1", "E1", "E2");
		// 대조 leg — 이번 요청의 별칭이 아니면 꼴이 같아도 세지 않는다(S9 · E9)
		assertThat(ReportCitationCheck.leftAliases(new ReportDraft("S9 · E9", Map.of()), ALIASES)).isEmpty();
	}

}
