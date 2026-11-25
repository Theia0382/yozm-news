package net.yozmnews.report.adapter.out.fake;

import static net.yozmnews.report.ReportFixtures.event;
import static net.yozmnews.report.ReportFixtures.input;
import static net.yozmnews.report.ReportFixtures.interests;
import static net.yozmnews.report.ReportFixtures.storyline;
import static net.yozmnews.report.ReportFixtures.userStoryline;
import static net.yozmnews.report.ReportFixtures.userStorylines;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.llm.GenerationStop;
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
import net.yozmnews.report.domain.prompt.ReportPrompts;
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
		ReportOutcome.Generated generated = generate(input(userStorylines(userStoryline(31, 902)), interests(44)));

		assertThat(JSON.readTree(generated.text())).isEqualTo(JSON.readTree("""
				{"sections":{
				  "storylines":{"title":"로컬 섹션 1","body":"로컬 대체물이 만든 본문이다.","refs":["S1","E1"]},
				  "interests":{"title":"로컬 섹션 2","body":"로컬 대체물이 만든 본문이다.","refs":["S2"]}}}"""));
		assertThat(generated.usage()).isEqualTo(new TokenUsage(0, 0));
		assertThat(generated.stop()).isEqualTo(GenerationStop.COMPLETE);
	}

	@Test
	void followsTheInputOrderOfTargets() {
		ReportOutcome.Generated generated = generate(
				input(userStorylines(userStoryline(44, 903), userStoryline(31, 902))));

		assertThat(JSON.readTree(generated.text()).get("sections").get("storylines").get("refs").toString())
			.isEqualTo("[\"S1\",\"E1\",\"S2\",\"E2\"]");
	}

	@Test
	void passesTheSameReadingAndCitationCheckWithManyOrNoTargets() {
		ReportInput many = input(userStorylines(userStoryline(31, 902)), interests(31, 44));
		ReportInput none = input(interests());

		assertThat(cite(many).sections()).containsExactly(
				new CitedSection(ReportSectionKind.STORYLINES, "로컬 섹션 1", "로컬 대체물이 만든 본문이다.",
						List.of(storyline(31), event(902))),
				new CitedSection(ReportSectionKind.INTERESTS, "로컬 섹션 2", "로컬 대체물이 만든 본문이다.",
						List.of(storyline(31), storyline(44))));
		CitedReport cited = cite(none);
		assertThat(cited.sections()).containsExactly(
				new CitedSection(ReportSectionKind.INTERESTS, "로컬 섹션 1", "로컬 대체물이 만든 본문이다.", List.of()));
		assertThat(cited.missingSections()).isZero();
		assertThat(cited.unknownRefs()).isZero();
	}

	private ReportOutcome.Generated generate(ReportInput input) {
		ReportAliases aliases = ReportAliases.of(input);
		ReportOutcome outcome = this.generator.generate(new ReportRequest(new UserId(7), ReportFixtures.MODEL,
				ReportFixtures.EFFORT, aliases, ReportPrompts.compose(input, aliases, ReportFixtures.MODEL)));
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
