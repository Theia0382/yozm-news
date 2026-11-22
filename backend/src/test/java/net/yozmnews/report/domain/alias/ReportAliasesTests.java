package net.yozmnews.report.domain.alias;

import static net.yozmnews.report.ReportFixtures.event;
import static net.yozmnews.report.ReportFixtures.input;
import static net.yozmnews.report.ReportFixtures.section;
import static net.yozmnews.report.ReportFixtures.storyline;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.input.InputSection;
import net.yozmnews.report.domain.input.ReportInput;
import org.junit.jupiter.api.Test;

/**
 * 생성의 입력과 별칭 — 입력 거부, 스토리라인 · 사건 별칭, 같은 대상의 같은 별칭, 갈래 없음.
 * <p>
 * 지키는 규칙: report-R3 · report-R4
 */
class ReportAliasesTests {

	@Test
	void namesStorylinesAndEventsInInputOrderAndKeepsOneAliasForTheSameTarget() {
		ReportAliases aliases = ReportAliases.of(input(
				section(ReportSectionKind.STORYLINES, storyline(31), event(902), storyline(44)),
				section(ReportSectionKind.COVERAGE, storyline(44), event(903))));

		assertThat(aliases.sections()).containsExactly(
				new AliasedSection(ReportSectionKind.STORYLINES, List.of("S1", "E1", "S2")),
				new AliasedSection(ReportSectionKind.COVERAGE, List.of("S2", "E2")));
		assertThat(aliases.target("S2")).contains(storyline(44));
		assertThat(aliases.target("E2")).contains(event(903));
		assertThat(aliases.target("s2")).isEmpty();
		assertThat(aliases.target("S3")).isEmpty();
	}

	@Test
	void hasNoSectionsOnlyWhenTheInputHasNoSection() {
		assertThat(ReportAliases.of(input()).hasNoSections()).isTrue();
		ReportAliases statsOnly = ReportAliases.of(input(section(ReportSectionKind.INTERESTS)));
		assertThat(statsOnly.hasNoSections()).isFalse();
		assertThat(statsOnly.sections()).containsExactly(new AliasedSection(ReportSectionKind.INTERESTS, List.of()));
	}

	@Test
	void refusesAnOverviewSectionARepeatedTargetARepeatedKindAndANonObjectSnapshot() {
		assertThatIllegalArgumentException().isThrownBy(() -> section(ReportSectionKind.OVERVIEW))
			.withMessage("개요는 입력 갈래가 아니다");
		assertThatIllegalArgumentException().isThrownBy(() -> section(ReportSectionKind.COVERAGE, event(1), event(1)))
			.withMessage("한 갈래에 같은 대상이 두 번 있다: COVERAGE");
		InputSection coverage = section(ReportSectionKind.COVERAGE);
		assertThatIllegalArgumentException().isThrownBy(() -> input(coverage, section(ReportSectionKind.COVERAGE)))
			.withMessage("갈래 종류가 겹친다: COVERAGE");
		for (String snapshot : List.of("[]", "1", "{} {}", "", "{")) {
			assertThatIllegalArgumentException().as(snapshot)
				.isThrownBy(() -> new ReportInput(List.of(coverage), snapshot))
				.withMessage("스냅숏이 JSON 객체 하나가 아니다");
		}
		assertThat(new ReportInput(List.of(coverage), "{}").statsSnapshot()).isEqualTo("{}");
	}

}
