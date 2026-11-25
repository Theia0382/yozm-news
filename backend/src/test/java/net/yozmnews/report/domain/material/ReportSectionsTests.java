package net.yozmnews.report.domain.material;

import static net.yozmnews.report.ReportFixtures.userStoryline;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import net.yozmnews.report.InterestShift;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.input.Count;
import net.yozmnews.report.domain.input.CoverageSection;
import net.yozmnews.report.domain.input.InputSection;
import net.yozmnews.report.domain.input.StorylinesSection;
import net.yozmnews.report.domain.input.TopicCoverageLine;
import net.yozmnews.report.domain.input.TopicInterestLine;
import net.yozmnews.report.domain.input.TopicSubscriptionLine;
import net.yozmnews.user.TopicSubscriptionAction;
import org.junit.jupiter.api.Test;

/**
 * 갈래 조건 · 순서 — 신호 없음 · 추적한 사용자 스토리라인 없음 · 재료 없음, 두 보도량은 다른 갈래. 지키는 규칙: report-R14
 */
class ReportSectionsTests {

	private static final List<TopicCoverageLine> TOPICS = List
		.of(new TopicCoverageLine("economy.finance", "금융", new Count(12, 7L)));

	private static final InterestLines.Lines NO_INTEREST = new InterestLines.Lines(List.of(), List.of(), List.of());

	@Test
	void putsTheThreeKindsInOrderWithTheTwoCoveragesInDifferentSections() {
		List<InputSection> sections = ReportSections.of(List.of(userStoryline(31, 902)), TOPICS, new InterestLines.Lines(
				List.of(new TopicInterestLine("economy.finance", "금융", 0.1, 0.3, InterestShift.ENTERED)), List.of(),
				List.of()));

		assertThat(sections).extracting(InputSection::kind)
			.containsExactly(ReportSectionKind.STORYLINES, ReportSectionKind.COVERAGE, ReportSectionKind.INTERESTS);
		// 사용자 스토리라인 보도량은 추적한 사용자 스토리라인 갈래에, 구독 주제 보도량은 보도량 갈래에 — 서로 더하지 않는다
		assertThat(((StorylinesSection) sections.get(0)).userStorylines()).containsExactly(userStoryline(31, 902));
		assertThat(((CoverageSection) sections.get(1)).topics()).containsExactly(
				new TopicCoverageLine("economy.finance", "금융", new Count(12, 7L)));
	}

	@Test
	void leavesOutAKindWithoutMaterial() {
		// 신호 없음 — 관심 변화 갈래가 없다
		assertThat(ReportSections.of(List.of(userStoryline(31)), TOPICS, NO_INTEREST)).extracting(InputSection::kind)
			.containsExactly(ReportSectionKind.STORYLINES, ReportSectionKind.COVERAGE);
		// 추적한 사용자 스토리라인 없음 — 추적한 사용자 스토리라인 갈래가 없다. 구독 변경만 있어도 관심 변화 갈래가 든다
		assertThat(ReportSections.of(List.of(), List.of(), new InterestLines.Lines(List.of(), List.of(),
				List.of(new TopicSubscriptionLine(Instant.parse("2026-11-18T00:00:00Z"), TopicSubscriptionAction.ADD,
						List.of("금융"))))))
			.extracting(InputSection::kind)
			.containsExactly(ReportSectionKind.INTERESTS);
		// 재료가 하나도 없으면 갈래가 없다 — 생성이 EMPTY 로 끝낸다
		assertThat(ReportSections.of(List.of(), List.of(), NO_INTEREST)).isEmpty();
	}

}
