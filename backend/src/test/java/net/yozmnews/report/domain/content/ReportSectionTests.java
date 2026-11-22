package net.yozmnews.report.domain.content;

import static net.yozmnews.report.ReportFixtures.event;
import static net.yozmnews.report.ReportFixtures.storyline;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.output.CitedReport;
import net.yozmnews.report.domain.output.CitedSection;
import org.junit.jupiter.api.Test;

/**
 * 내용 저장 꼴 — 개요 위치 0, 갈래 섹션은 입력 순서, 인용은 스토리라인 또는 사건 하나.
 * <p>
 * 지키는 규칙: report-R9
 */
class ReportSectionTests {

	@Test
	void splitsIntoTheOverviewAndTheKindSectionsInOrder() {
		List<ReportSection> sections = ReportSection.of(new ReportId(5), new CitedReport("개요",
				List.of(new CitedSection(ReportSectionKind.COVERAGE, "보도량", "본문 하나", List.of(event(902), storyline(31))),
						new CitedSection(ReportSectionKind.INTERESTS, "관심", "본문 둘", List.of())),
				1, 0));

		assertThat(sections).extracting(ReportSection::kind, ReportSection::position, ReportSection::body)
			.containsExactly(tuple(ReportSectionKind.OVERVIEW, 0, "개요"),
					tuple(ReportSectionKind.COVERAGE, 1, "본문 하나"),
					tuple(ReportSectionKind.INTERESTS, 2, "본문 둘"));
		assertThat(sections.get(0).title()).isEmpty();
		assertThat(sections.get(0).items()).isEmpty();
		assertThat(sections.get(1).title()).contains("보도량");
		assertThat(sections.get(1).items()).containsExactly(new ReportItem(null, 902L), new ReportItem(31L, null));
		assertThat(sections.get(1).items()).extracting(ReportItem::citation).containsExactly(event(902), storyline(31));
		assertThat(sections).allSatisfy(section -> assertThat(section.reportId()).isEqualTo(new ReportId(5)));
	}

	@Test
	void anItemPointsToExactlyOneTarget() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ReportItem(1L, 2L))
			.withMessage("스토리라인과 사건 가운데 하나만 가리킨다");
		assertThatIllegalArgumentException().isThrownBy(() -> new ReportItem(null, null))
			.withMessage("스토리라인과 사건 가운데 하나만 가리킨다");
	}

}
