package net.yozmnews.report.domain.output;

import static net.yozmnews.report.ReportFixtures.input;
import static net.yozmnews.report.ReportFixtures.interests;
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
 * 리포트 출력 읽기 — 정상 · 빠진 갈래 · 서술에 없는 칸 · 상한 넘는 칸과 별칭 원소 · 읽지 못함.
 * <p>
 * 지키는 규칙: report-R5 · report-R6
 */
class ReportOutputReaderTests {

	private static final OutputShape.Obj ROOT = ReportOutputSchema.root(ReportAliases.of(input(
			userStorylines(userStoryline(31, 902)), interests())));

	@Test
	void readsEverySectionInto() {
		ReportOutputRead.Read read = read("""
				{"sections":{
				  "storylines":{"title":"구독 흐름","body":"본문 하나","refs":["S1"," E1 "]},
				  "interests":{"title":"관심","body":"본문 둘","refs":[]}}}""");

		assertThat(read.overLimit()).isZero();
		assertThat(read.draft()).isEqualTo(new ReportDraft(
				Map.of(ReportSectionKind.STORYLINES, new SectionDraft("구독 흐름", "본문 하나", List.of("S1", "E1")),
						ReportSectionKind.INTERESTS, new SectionDraft("관심", "본문 둘", List.of()))));
	}

	@Test
	void aMissingOrNullSectionIsLeftOutAndAnUndescribedFieldIsIgnored() {
		// 서술에 없는 칸(개요 · 구독 주제 보도량)은 읽지 않는다
		ReportOutputRead.Read read = read("""
				{"overview":"개요","sections":{"storylines":null,"coverage":{"title":"보도량","body":"본문","refs":[]}}}""");

		assertThat(read.draft().sections()).isEmpty();
	}

	@Test
	void dropsTheSectionWhoseTitleIsOverTheLimitAndKeepsTheOneAtTheLimit() {
		String atLimit = "가".repeat(200);
		ReportOutputRead.Read read = read("{\"sections\":{"
				+ "\"storylines\":{\"title\":\"" + atLimit + "\",\"body\":\"본문\",\"refs\":[]},"
				+ "\"interests\":{\"title\":\"" + atLimit + "나\",\"body\":\"본문\",\"refs\":[]}}}");

		assertThat(read.draft().sections()).containsOnlyKeys(ReportSectionKind.STORYLINES);
		assertThat(read.overLimit()).isEqualTo(1);
	}

	@Test
	void dropsTheSectionWhoseBodyIsOverTheLimit() {
		ReportOutputRead.Read read = read("{\"sections\":{"
				+ "\"storylines\":{\"title\":\"제목\",\"body\":\"" + "가".repeat(3_001) + "\",\"refs\":[]}}}");

		assertThat(read.draft().sections()).isEmpty();
		assertThat(read.overLimit()).isEqualTo(1);
	}

	@Test
	void dropsOnlyTheAliasElementThatIsOverTheLimitAndKeepsABlankAlias() {
		ReportOutputRead.Read read = read("{\"sections\":{\"storylines\":{\"title\":\"제목\","
				+ "\"body\":\"본문\",\"refs\":[\"S1\",\"" + "S".repeat(17) + "\",\" \"]}}}");

		assertThat(read.draft().sections().get(ReportSectionKind.STORYLINES).refs()).containsExactly("S1", "");
		assertThat(read.overLimit()).isEqualTo(1);
	}

	@Test
	void anUnreadableOutputCarriesAShortDiagnosis() {
		assertThat(ReportOutputReader.read("{\"sections\":{\"storylines\":{\"title\":\" \",\"body\":\"본문\",\"refs\":[]}}}",
				ROOT))
			.isEqualTo(new ReportOutputRead.Unreadable("$.sections.storylines.title: 비었다"));
		assertThat(ReportOutputReader.read("{\"sections\":{},\"sections\":{}}", ROOT))
			.isEqualTo(new ReportOutputRead.Unreadable("JSON 이 아니다"));
		assertThat(ReportOutputReader.read("{\"overview\":\"개요\"}", ROOT))
			.isEqualTo(new ReportOutputRead.Unreadable("$.sections: 없다"));
		assertThat(ReportOutputReader.read("{\"sections\":{\"storylines\":{\"title\":\"제목\","
				+ "\"body\":\"본문\"}}}", ROOT))
			.isEqualTo(new ReportOutputRead.Unreadable("$.sections.storylines.refs: 없다"));
		assertThat(ReportOutputReader.read("[]", ROOT)).isEqualTo(new ReportOutputRead.Unreadable("$: 객체가 아니다"));
		assertThat(ReportOutputReader.read("{\"sections\":[]}", ROOT))
			.isEqualTo(new ReportOutputRead.Unreadable("$.sections: 객체가 아니다"));
	}

	private static ReportOutputRead.Read read(String text) {
		ReportOutputRead read = ReportOutputReader.read(text, ROOT);
		assertThat(read).isInstanceOf(ReportOutputRead.Read.class);
		return (ReportOutputRead.Read) read;
	}

}
