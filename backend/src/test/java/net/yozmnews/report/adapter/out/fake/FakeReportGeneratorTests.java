package net.yozmnews.report.adapter.out.fake;

import static net.yozmnews.report.ReportFixtures.event;
import static net.yozmnews.report.ReportFixtures.input;
import static net.yozmnews.report.ReportFixtures.section;
import static net.yozmnews.report.ReportFixtures.storyline;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.llm.GenerationStop;
import net.yozmnews.platform.StructuredOutputSchema;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportFixtures;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.alias.ReportAliases;
import net.yozmnews.report.domain.generator.ReportOutcome;
import net.yozmnews.report.domain.generator.ReportRequest;
import net.yozmnews.report.domain.generator.TokenUsage;
import net.yozmnews.report.domain.input.ReportInput;
import net.yozmnews.report.domain.output.CitedReport;
import net.yozmnews.report.domain.output.CitedSection;
import net.yozmnews.report.domain.output.ReportCitationCheck;
import net.yozmnews.report.domain.output.ReportOutputRead;
import net.yozmnews.report.domain.output.ReportOutputReader;
import net.yozmnews.report.domain.output.ReportOutputSchema;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 리포트 생성기의 로컬 대체물 — 고정 출력, 갈래 대상의 별칭으로만 인용, 실제 생성기와 같은 읽기 · 대조를 지남.
 * <p>
 * 지키는 규칙: report-R10
 */
class FakeReportGeneratorTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final FakeReportGenerator generator = new FakeReportGenerator();

	@Test
	void writesEverySectionWithTheAliasesOfItsTargets() {
		ReportOutcome.Generated generated = generate(input(
				section(ReportSectionKind.STORYLINES, storyline(31), event(902)),
				section(ReportSectionKind.INTERESTS),
				section(ReportSectionKind.COVERAGE, event(903))));

		assertThat(JSON.readTree(generated.text())).isEqualTo(JSON.readTree("""
				{"overview":"로컬 대체물이 만든 리포트다. 섹션 3개.","sections":{
				  "storylines":{"title":"로컬 섹션 1","body":"로컬 대체물이 만든 본문이다.","refs":["S1","E1"]},
				  "interests":{"title":"로컬 섹션 2","body":"로컬 대체물이 만든 본문이다.","refs":[]},
				  "coverage":{"title":"로컬 섹션 3","body":"로컬 대체물이 만든 본문이다.","refs":["E2"]}}}"""));
		assertThat(generated.usage()).isEqualTo(new TokenUsage(0, 0));
		assertThat(generated.stop()).isEqualTo(GenerationStop.COMPLETE);
	}

	@Test
	void followsTheInputOrderOfTargets() {
		ReportOutcome.Generated generated = generate(
				input(section(ReportSectionKind.STORYLINES, event(902), storyline(31))));

		assertThat(JSON.readTree(generated.text()).get("sections").get("storylines").get("refs").toString())
			.isEqualTo("[\"E1\",\"S1\"]");
	}

	@Test
	void passesTheSameReadingAndCitationCheckWithManyOrNoTargets() {
		ReportInput many = input(section(ReportSectionKind.STORYLINES, storyline(31), event(902)),
				section(ReportSectionKind.RISE_AND_FALL, storyline(31), event(903)));
		ReportInput none = input(section(ReportSectionKind.INTERESTS));

		assertThat(cite(many).sections()).containsExactly(
				new CitedSection(ReportSectionKind.STORYLINES, "로컬 섹션 1", "로컬 대체물이 만든 본문이다.",
						List.of(storyline(31), event(902))),
				new CitedSection(ReportSectionKind.RISE_AND_FALL, "로컬 섹션 2", "로컬 대체물이 만든 본문이다.",
						List.of(storyline(31), event(903))));
		CitedReport cited = cite(none);
		assertThat(cited.overview()).isEqualTo("로컬 대체물이 만든 리포트다. 섹션 1개.");
		assertThat(cited.sections()).containsExactly(
				new CitedSection(ReportSectionKind.INTERESTS, "로컬 섹션 1", "로컬 대체물이 만든 본문이다.", List.of()));
		assertThat(cited.missingSections()).isZero();
		assertThat(cited.unknownRefs()).isZero();
	}

	private ReportOutcome.Generated generate(ReportInput input) {
		ReportAliases aliases = ReportAliases.of(input);
		ReportOutcome outcome = this.generator.generate(new ReportRequest(new UserId(7), ReportFixtures.MODEL, aliases,
				StructuredOutputSchema.json(ReportOutputSchema.root(aliases))));
		assertThat(outcome).isInstanceOf(ReportOutcome.Generated.class);
		return (ReportOutcome.Generated) outcome;
	}

	private CitedReport cite(ReportInput input) {
		ReportAliases aliases = ReportAliases.of(input);
		ReportOutputRead read = ReportOutputReader.read(generate(input).text(), ReportOutputSchema.root(aliases));
		assertThat(read).isInstanceOf(ReportOutputRead.Read.class);
		return ReportCitationCheck.check(((ReportOutputRead.Read) read).draft(), aliases);
	}

}
