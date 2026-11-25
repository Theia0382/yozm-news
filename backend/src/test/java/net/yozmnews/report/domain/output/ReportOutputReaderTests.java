package net.yozmnews.report.domain.output;

import static net.yozmnews.report.ReportFixtures.coverage;
import static net.yozmnews.report.ReportFixtures.input;
import static net.yozmnews.report.ReportFixtures.userStoryline;
import static net.yozmnews.report.ReportFixtures.userStorylines;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import net.yozmnews.platform.OutputShape;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.alias.ReportAliases;
import org.junit.jupiter.api.Test;

/**
 * 리포트 출력 읽기 — 정상 · 빠진 갈래 · 상한 넘는 칸과 별칭 원소 · 읽지 못함.
 * <p>
 * 지키는 규칙: report-R5 · report-R6
 */
class ReportOutputReaderTests {

	private static final OutputShape.Obj ROOT = ReportOutputSchema.root(ReportAliases.of(input(
			userStorylines(userStoryline(31, 902)), coverage())));

	@Test
	void readsTheOverviewAndEverySectionInto() {
		ReportOutputRead.Read read = read("""
				{"overview":" 이번 주 개요 ","sections":{
				  "storylines":{"title":"구독 흐름","body":"본문 하나","refs":["S1"," E1 "]},
				  "coverage":{"title":"보도량","body":"본문 둘","refs":[]}}}""");

		assertThat(read.overLimit()).isZero();
		assertThat(read.draft()).isEqualTo(new ReportDraft("이번 주 개요",
				Map.of(ReportSectionKind.STORYLINES, new SectionDraft("구독 흐름", "본문 하나", List.of("S1", "E1")),
						ReportSectionKind.COVERAGE, new SectionDraft("보도량", "본문 둘", List.of()))));
	}

	@Test
	void aMissingOrNullSectionIsLeftOutAndAnUndescribedOneIsIgnored() {
		ReportOutputRead.Read read = read("""
				{"overview":"개요","sections":{"storylines":null,"interests":{"title":"관심","body":"본문","refs":[]}}}""");

		assertThat(read.draft().sections()).isEmpty();
	}

	@Test
	void dropsTheSectionWhoseTitleIsOverTheLimitAndKeepsTheOneAtTheLimit() {
		String atLimit = "가".repeat(200);
		ReportOutputRead.Read read = read("{\"overview\":\"개요\",\"sections\":{"
				+ "\"storylines\":{\"title\":\"" + atLimit + "\",\"body\":\"본문\",\"refs\":[]},"
				+ "\"coverage\":{\"title\":\"" + atLimit + "나\",\"body\":\"본문\",\"refs\":[]}}}");

		assertThat(read.draft().sections()).containsOnlyKeys(ReportSectionKind.STORYLINES);
		assertThat(read.overLimit()).isEqualTo(1);
	}

	@Test
	void dropsTheSectionWhoseBodyIsOverTheLimit() {
		ReportOutputRead.Read read = read("{\"overview\":\"개요\",\"sections\":{"
				+ "\"storylines\":{\"title\":\"제목\",\"body\":\"" + "가".repeat(3_001) + "\",\"refs\":[]}}}");

		assertThat(read.draft().sections()).isEmpty();
		assertThat(read.overLimit()).isEqualTo(1);
	}

	@Test
	void dropsOnlyTheAliasElementThatIsOverTheLimitAndKeepsABlankAlias() {
		ReportOutputRead.Read read = read("{\"overview\":\"개요\",\"sections\":{\"storylines\":{\"title\":\"제목\","
				+ "\"body\":\"본문\",\"refs\":[\"S1\",\"" + "S".repeat(17) + "\",\" \"]}}}");

		assertThat(read.draft().sections().get(ReportSectionKind.STORYLINES).refs()).containsExactly("S1", "");
		assertThat(read.overLimit()).isEqualTo(1);
	}

	@Test
	void anUnreadableOutputCarriesAShortDiagnosis() {
		assertThat(ReportOutputReader.read("{\"overview\":\" \",\"sections\":{}}", ROOT))
			.isEqualTo(new ReportOutputRead.Unreadable("$.overview: 비었다"));
		assertThat(ReportOutputReader.read("{\"overview\":\"개요\",\"overview\":\"또\",\"sections\":{}}", ROOT))
			.isEqualTo(new ReportOutputRead.Unreadable("JSON 이 아니다"));
		assertThat(ReportOutputReader.read("{\"overview\":\"개요\"}", ROOT))
			.isEqualTo(new ReportOutputRead.Unreadable("$.sections: 없다"));
		assertThat(ReportOutputReader.read("{\"overview\":\"개요\",\"sections\":{\"storylines\":{\"title\":\"제목\","
				+ "\"body\":\"본문\"}}}", ROOT))
			.isEqualTo(new ReportOutputRead.Unreadable("$.sections.storylines.refs: 없다"));
		assertThat(ReportOutputReader.read("{\"overview\":\"" + "가".repeat(1_501) + "\",\"sections\":{}}", ROOT))
			.isEqualTo(new ReportOutputRead.Unreadable("$.overview: 1500 코드 포인트를 넘는다"));
		assertThat(ReportOutputReader.read("[]", ROOT)).isEqualTo(new ReportOutputRead.Unreadable("$: 객체가 아니다"));
		assertThat(ReportOutputReader.read("{\"overview\":\"개요\",\"sections\":[]}", ROOT))
			.isEqualTo(new ReportOutputRead.Unreadable("$.sections: 객체가 아니다"));
	}

	private static ReportOutputRead.Read read(String text) {
		ReportOutputRead read = ReportOutputReader.read(text, ROOT);
		assertThat(read).isInstanceOf(ReportOutputRead.Read.class);
		return (ReportOutputRead.Read) read;
	}

}
