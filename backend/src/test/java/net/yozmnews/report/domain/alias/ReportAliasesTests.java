package net.yozmnews.report.domain.alias;

import static net.yozmnews.report.ReportFixtures.coverage;
import static net.yozmnews.report.ReportFixtures.event;
import static net.yozmnews.report.ReportFixtures.flow;
import static net.yozmnews.report.ReportFixtures.flows;
import static net.yozmnews.report.ReportFixtures.input;
import static net.yozmnews.report.ReportFixtures.interests;
import static net.yozmnews.report.ReportFixtures.PERIOD;
import static net.yozmnews.report.ReportFixtures.storyline;
import static net.yozmnews.report.ReportFixtures.USER;
import static net.yozmnews.report.ReportFixtures.ZONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.input.InputSection;
import net.yozmnews.report.domain.input.ReportInput;
import net.yozmnews.report.domain.input.StorylinesSection;
import org.junit.jupiter.api.Test;

/**
 * 생성의 입력과 별칭 — 입력 거부, 스토리라인 · 사건 별칭, 같은 대상의 같은 별칭, 갈래 없음.
 * <p>
 * 지키는 규칙: report-R3 · report-R4
 */
class ReportAliasesTests {

	@Test
	void namesStorylinesAndEventsInInputOrderAndKeepsOneAliasForTheSameTarget() {
		ReportAliases aliases = ReportAliases.of(input(flows(flow(31, 902), flow(44, 903)), interests(44, 31)));

		// 흐름의 헤드 → 그 흐름의 사건 순 — 다른 갈래에 다시 든 대상은 처음 매긴 별칭 하나
		assertThat(aliases.sections()).containsExactly(
				new AliasedSection(ReportSectionKind.STORYLINES, List.of("S1", "E1", "S2", "E2")),
				new AliasedSection(ReportSectionKind.INTERESTS, List.of("S2", "S1")));
		assertThat(aliases.target("S2")).contains(storyline(44));
		assertThat(aliases.target("E2")).contains(event(903));
		assertThat(aliases.target("s2")).isEmpty();
		assertThat(aliases.target("S3")).isEmpty();
	}

	@Test
	void givesTheAliasOfATargetAndRefusesATargetOutsideTheInput() {
		ReportAliases aliases = ReportAliases.of(input(flows(flow(31, 902), flow(44, 903)), interests(44, 31)));

		assertThat(aliases.alias(storyline(31))).isEqualTo("S1");
		assertThat(aliases.alias(storyline(44))).isEqualTo("S2");
		assertThat(aliases.alias(event(903))).isEqualTo("E2");
		assertThatIllegalArgumentException().isThrownBy(() -> aliases.alias(storyline(99)));
		assertThatIllegalArgumentException().isThrownBy(() -> aliases.alias(event(31)));
	}

	@Test
	void hasNoSectionsOnlyWhenTheInputHasNoSection() {
		assertThat(ReportAliases.of(input()).hasNoSections()).isTrue();
		ReportAliases statsOnly = ReportAliases.of(input(coverage()));
		assertThat(statsOnly.hasNoSections()).isFalse();
		assertThat(statsOnly.sections()).containsExactly(new AliasedSection(ReportSectionKind.COVERAGE, List.of()));
	}

	@Test
	void keepsTheFirstOfARepeatedTargetAndRefusesEmptyMaterialARepeatedKindAndANonObjectSnapshot() {
		// 한 갈래 안 같은 대상은 처음 것만 — 사건이 두 흐름에 들 수 있다
		assertThat(flows(flow(31, 902), flow(44, 902)).targets()).containsExactly(storyline(31), event(902),
				storyline(44));
		assertThatIllegalArgumentException().isThrownBy(() -> new StorylinesSection(List.of()))
			.withMessage("추적한 흐름의 갈래에 흐름이 없다");
		InputSection coverage = coverage();
		assertThatIllegalArgumentException().isThrownBy(() -> input(coverage, coverage()))
			.withMessage("갈래 종류가 겹친다: COVERAGE");
		for (String snapshot : List.of("[]", "1", "{} {}", "", "{")) {
			assertThatIllegalArgumentException().as(snapshot)
				.isThrownBy(() -> new ReportInput(USER, PERIOD, ZONE, List.of(coverage), List.of(), snapshot, null))
				.withMessage("스냅숏이 JSON 객체 하나가 아니다");
		}
		assertThat(new ReportInput(USER, PERIOD, ZONE, List.of(coverage), List.of(), "{}", null).statsSnapshot())
			.isEqualTo("{}");
	}

}
