package net.yozmnews.report.domain.prompt;

import static net.yozmnews.report.ReportFixtures.coverage;
import static net.yozmnews.report.ReportFixtures.flow;
import static net.yozmnews.report.ReportFixtures.flows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import net.yozmnews.event.EventId;
import net.yozmnews.report.ReportFixtures;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.input.Count;
import net.yozmnews.report.domain.input.EventLine;
import net.yozmnews.report.domain.input.FlowInterestLine;
import net.yozmnews.report.domain.input.FlowMaterial;
import net.yozmnews.report.domain.input.FlowSummary;
import net.yozmnews.report.domain.input.InputSection;
import net.yozmnews.report.domain.input.InterestShift;
import net.yozmnews.report.domain.input.InterestsSection;
import net.yozmnews.report.domain.input.PriorReport;
import net.yozmnews.report.domain.input.PriorSection;
import net.yozmnews.report.domain.input.ReportInput;
import net.yozmnews.report.domain.input.StorylinesSection;
import net.yozmnews.report.domain.input.TopicInterestLine;
import net.yozmnews.report.domain.prompt.FittedInput.Trimmed;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineState;
import org.junit.jupiter.api.Test;

/**
 * 입력 맞추기 — docs/specs/report.md §5 · report-R22
 * <p>
 * 입력마다 한 단계가 크기를 차지하게 만들어 그 단계에서 멈추는지와, 그 단계가 무엇을 남겼는지를 단언한다 — 크기를 다시 계산하지 않는다. 예산은
 * 차지하는 덩어리와 나머지의 어림(코드 포인트 하나에 토큰 하나)보다 넉넉히 떨어진 리터럴이다.
 */
class ReportInputFitTests {

	private static final Instant UPDATED = Instant.parse("2026-11-20T00:00:00Z");

	@Test
	void leavesAnInputWithinTheBudgetAsItIs() {
		ReportInput input = ReportFixtures.input(flows(flow(31, 902)), coverage());

		FittedInput fitted = ReportInputFit.fit(input, 72_000);

		assertThat(fitted.input()).isSameAs(input);
		assertThat(fitted.trimmed()).isEqualTo(Trimmed.NONE);
		assertThat(fitted.trimmedAnything()).isFalse();
		assertThat(fitted.overBudget()).isFalse();
	}

	@Test
	void cutsTheBodiesOfEachPriorToItsShareAndKeepsOverviewsAndTitles() {
		// 직전 글 두 편 — 본문 5,000 자씩. 예산 4,000 의 몫 1,200 을 두 편이 600 씩
		PriorReport first = prior(1, "가".repeat(5_000));
		PriorReport second = prior(2, "나".repeat(5_000));
		ReportInput input = input(List.of(coverage()), List.of(first, second));

		FittedInput fitted = ReportInputFit.fit(input, 4_000);

		assertThat(fitted.trimmed()).isEqualTo(new Trimmed(2, 0, 0, 0, 0, 0));
		assertThat(fitted.overBudget()).isFalse();
		for (PriorReport prior : fitted.input().priors()) {
			assertThat(prior.overview()).isEqualTo("지난 개요");
			assertThat(prior.sections()).singleElement().satisfies(section -> {
				assertThat(section.title()).isEqualTo("지난 제목");
				assertThat(section.body().length()).isBetween(400, 600);
			});
		}
	}

	@Test
	void doesNotPassTheShareLeftByAShortPriorToALongOne() {
		// 짧은 편이 몫 600 을 다 쓰지 않아도 긴 편은 자기 몫 600 까지만 — 남은 몫은 재료가 쓴다
		PriorReport shortPrior = prior(1, "가".repeat(50));
		PriorReport longPrior = prior(2, "나".repeat(5_000));
		ReportInput input = input(List.of(coverage()), List.of(shortPrior, longPrior));

		FittedInput fitted = ReportInputFit.fit(input, 4_000);

		assertThat(fitted.trimmed()).isEqualTo(new Trimmed(1, 0, 0, 0, 0, 0));
		assertThat(fitted.input().priors().get(0)).isEqualTo(shortPrior);
		assertThat(fitted.input().priors().get(1).sections()).singleElement()
			.satisfies(section -> assertThat(section.body().length()).isBetween(400, 600));
	}

	@Test
	void dropsTheSummariesOfUntrackedFlowsFirstAmongTheMaterial() {
		// 추적하지 않는 흐름 셋의 정리가 3,000 자씩 — 추적 흐름(헤드 1)의 관심 줄 정리는 사용자 구획에 실리지 않으므로 그대로 둔다
		List<FlowInterestLine> lines = new ArrayList<>();
		lines.add(new FlowInterestLine(new StorylineId(1), summary("다".repeat(3_000)), 0.2, 0.4, InterestShift.NONE));
		LongStream.of(11, 12, 13)
			.forEach(head -> lines.add(new FlowInterestLine(new StorylineId(head), summary("다".repeat(3_000)), null, 0.3,
					InterestShift.ENTERED)));
		ReportInput input = input(List.of(flows(flow(1)), new InterestsSection(List.of(), lines, List.of())), List.of());

		FittedInput fitted = ReportInputFit.fit(input, 3_000);

		assertThat(fitted.trimmed()).isEqualTo(new Trimmed(0, 3, 0, 0, 0, 0));
		assertThat(fitted.overBudget()).isFalse();
		List<FlowInterestLine> after = interests(fitted.input()).flows();
		assertThat(after).hasSize(4);
		assertThat(after.get(0).summary()).isNotNull();
		assertThat(after.subList(1, 4)).allSatisfy(line -> assertThat(line.summary()).isNull());
	}

	@Test
	void dropsSteadyFlowInterestLinesFromTheLargestHead() {
		// 변화 없는 흐름 관심 줄 200(헤드 100 ~ 299)과 새로 생긴 관심 하나(헤드 5), 주제 관심 줄 하나
		List<FlowInterestLine> lines = new ArrayList<>();
		lines.add(new FlowInterestLine(new StorylineId(5), null, null, 0.3, InterestShift.ENTERED));
		LongStream.range(100, 300)
			.forEach(head -> lines.add(new FlowInterestLine(new StorylineId(head), null, 0.4, 0.6, InterestShift.NONE)));
		InterestsSection interests = new InterestsSection(
				List.of(new TopicInterestLine("economy.finance", "금융", 0.1, 0.3, InterestShift.ENTERED)), lines,
				List.of());
		ReportInput input = input(List.of(interests), List.of());

		FittedInput fitted = ReportInputFit.fit(input, 3_000);

		Trimmed trimmed = fitted.trimmed();
		assertThat(trimmed.flowInterestLinesDropped()).isBetween(1, 199);
		assertThat(trimmed).isEqualTo(new Trimmed(0, 0, trimmed.flowInterestLinesDropped(), 0, 0, 0));
		assertThat(fitted.overBudget()).isFalse();
		List<Long> heads = interests(fitted.input()).flows().stream().map(line -> line.head().value()).toList();
		int steadyLeft = 200 - trimmed.flowInterestLinesDropped();
		List<Long> expected = new ArrayList<>(List.of(5L));
		LongStream.range(100, 100 + steadyLeft).forEach(expected::add);
		assertThat(heads).containsExactlyElementsOf(expected);
	}

	@Test
	void shortensEveryFlowToTheSameNumberOfEventLinesKeepingTheFront() {
		// 흐름 넷 · 사건 줄 열씩 — 제목 300 자
		List<FlowMaterial> flows = LongStream.rangeClosed(1, 4).mapToObj(head -> flowWithEvents(head, 10, 300)).toList();
		ReportInput input = input(List.of(new StorylinesSection(flows)), List.of());

		FittedInput fitted = ReportInputFit.fit(input, 6_000);

		List<FlowMaterial> after = storylines(fitted.input()).flows();
		int kept = after.get(0).events().size();
		assertThat(kept).isBetween(1, 9);
		assertThat(after).allSatisfy(flow -> assertThat(flow.events()).hasSize(kept));
		assertThat(after.get(2).events()).isEqualTo(flows.get(2).events().subList(0, kept));
		assertThat(fitted.trimmed()).isEqualTo(new Trimmed(0, 0, 0, 40 - 4 * kept, 0, 0));
		assertThat(fitted.overBudget()).isFalse();
	}

	@Test
	void emptiesTheFlowOverviewsKeepingTitlesAndRecentDevelopments() {
		// 흐름 열 · 사건 줄 없음 · 흐름 요약 1,000 자씩
		List<FlowMaterial> flows = LongStream.rangeClosed(1, 10)
			.mapToObj(head -> trackedFlow(head, 10, new FlowSummary("제목 " + head, "라".repeat(1_000), "최근 " + head,
					UPDATED)))
			.toList();
		ReportInput input = input(List.of(new StorylinesSection(flows)), List.of());

		FittedInput fitted = ReportInputFit.fit(input, 2_000);

		assertThat(fitted.trimmed()).isEqualTo(new Trimmed(0, 0, 0, 0, 10, 0));
		assertThat(fitted.overBudget()).isFalse();
		assertThat(storylines(fitted.input()).flows()).allSatisfy(flow -> {
			assertThat(flow.summary().summary()).isEmpty();
			assertThat(flow.summary().title()).startsWith("제목 ");
			assertThat(flow.summary().recent()).startsWith("최근 ");
		});
	}

	@Test
	void dropsTrackedFlowsWithTheLeastCoverageAndTheirInterestSummaries() {
		// 흐름 다섯 — 최근 전개 1,000 자씩(덜어 낼 수 없는 칸), 보도량 50 · 10 · 30 · 10 · 40(헤드 1 ~ 5)
		long[] articles = { 50, 10, 30, 10, 40 };
		List<FlowMaterial> flows = IntStream.range(0, 5)
			.mapToObj(i -> trackedFlow(i + 1, articles[i], new FlowSummary("t", "", "마".repeat(1_000), UPDATED)))
			.toList();
		// 헤드 4 의 흐름 관심 줄 — 추적 흐름이라 정리는 실리지 않다가, 흐름을 빼면 같이 뺀다
		InterestsSection interests = new InterestsSection(List.of(),
				List.of(new FlowInterestLine(new StorylineId(4), summary("짧은 요약"), 0.2, 0.5, InterestShift.ENTERED)),
				List.of());
		ReportInput input = input(List.of(new StorylinesSection(flows), interests), List.of());

		FittedInput fitted = ReportInputFit.fit(input, 2_500);

		// 보도량이 적은 순, 같으면 헤드가 큰 것부터 — 4(10) · 2(10) · 3(30)
		assertThat(storylines(fitted.input()).flows()).extracting(flow -> flow.head().value()).containsExactly(1L, 5L);
		assertThat(fitted.trimmed()).isEqualTo(new Trimmed(0, 1, 0, 0, 0, 3));
		assertThat(interests(fitted.input()).flows()).singleElement()
			.satisfies(line -> assertThat(line.summary()).isNull());
		assertThat(fitted.overBudget()).isFalse();
	}

	@Test
	void keepsALineInEverySectionAndReportsOverBudgetWhenNothingFits() {
		List<FlowInterestLine> lines = LongStream.range(100, 103)
			.mapToObj(head -> new FlowInterestLine(new StorylineId(head), null, 0.4, 0.6, InterestShift.NONE))
			.toList();
		ReportInput input = input(List.of(flows(flow(31, 902), flow(32, 903)), coverage(),
				new InterestsSection(List.of(), lines, List.of())), List.of());

		FittedInput fitted = ReportInputFit.fit(input, 0);

		assertThat(fitted.overBudget()).isTrue();
		ReportInput after = fitted.input();
		assertThat(after.sections()).extracting(InputSection::kind)
			.containsExactly(ReportSectionKind.STORYLINES, ReportSectionKind.COVERAGE, ReportSectionKind.INTERESTS);
		assertThat(storylines(after).flows()).hasSize(1);
		assertThat(interests(after).flows()).hasSize(1);
		assertThat(after.user()).isEqualTo(input.user());
		assertThat(after.period()).isEqualTo(input.period());
		assertThat(after.zone()).isEqualTo(input.zone());
		assertThat(after.statsSnapshot()).isEqualTo(input.statsSnapshot());
	}

	@Test
	void fitsTheLargestMaterialWithinTheMonthlyBudget() {
		// 추적 흐름 100(사건 줄 10 · 정리 상한) · 흐름 관심 줄 300 · 직전 글 일곱(최대 꼴)
		List<FlowMaterial> flows = LongStream.rangeClosed(1, 100)
			.mapToObj(head -> {
				FlowMaterial flow = flowWithEvents(head, 10, 50);
				return new FlowMaterial(flow.head(), flow.tops(),
						new FlowSummary("바".repeat(120), "사".repeat(1_200), "아".repeat(800), UPDATED), flow.headState(),
						false, flow.articles(), flow.events());
			})
			.toList();
		List<FlowInterestLine> lines = LongStream.rangeClosed(1, 300)
			.mapToObj(head -> new FlowInterestLine(new StorylineId(head),
					new FlowSummary("바".repeat(120), "사".repeat(1_200), "아".repeat(800), UPDATED), 0.4, 0.6,
					InterestShift.NONE))
			.toList();
		List<PriorReport> priors = LongStream.rangeClosed(1, 7)
			.mapToObj(id -> new PriorReport(new ReportId(id), ReportPeriodKind.WEEK, Instant.parse("2026-11-09T00:00:00Z"),
					Instant.parse("2026-11-16T00:00:00Z"), "자".repeat(1_500),
					List.of(new PriorSection(ReportSectionKind.STORYLINES, "차".repeat(200), "카".repeat(3_000)),
							new PriorSection(ReportSectionKind.COVERAGE, "차".repeat(200), "카".repeat(3_000)),
							new PriorSection(ReportSectionKind.INTERESTS, "차".repeat(200), "카".repeat(3_000)))))
			.toList();
		ReportInput input = input(List.of(new StorylinesSection(flows), coverage(),
				new InterestsSection(List.of(), lines, List.of())), priors);

		FittedInput fitted = ReportInputFit.fit(input, 112_000);

		assertThat(fitted.overBudget()).isFalse();
		assertThat(ReportInputFit.tokens(fitted.input())).isLessThanOrEqualTo(112_000);
		assertThat(fitted.trimmed().priorsCut()).isEqualTo(7);
	}

	@Test
	void refusesANegativeBudget() {
		ReportInput input = ReportFixtures.input(coverage());

		assertThatThrownBy(() -> ReportInputFit.fit(input, -1)).isInstanceOf(IllegalArgumentException.class);
	}

	private static ReportInput input(List<InputSection> sections, List<PriorReport> priors) {
		return new ReportInput(ReportFixtures.USER, ReportFixtures.PERIOD, ReportFixtures.ZONE, sections, priors,
				ReportFixtures.SNAPSHOT);
	}

	private static PriorReport prior(long id, String body) {
		return new PriorReport(new ReportId(id), ReportPeriodKind.WEEK, Instant.parse("2026-11-09T00:00:00Z"),
				Instant.parse("2026-11-16T00:00:00Z"), "지난 개요",
				List.of(new PriorSection(ReportSectionKind.STORYLINES, "지난 제목", body)));
	}

	private static FlowSummary summary(String text) {
		return new FlowSummary("제목", text, "최근", UPDATED);
	}

	private static FlowMaterial trackedFlow(long head, long articles, FlowSummary summary) {
		return new FlowMaterial(new StorylineId(head), Set.of(new StorylineId(head)), summary, StorylineState.ACTIVE,
				false, new Count(articles, null), List.of());
	}

	/** 사건 줄이 {@code events} 개인 흐름 — 사건 ID 는 헤드 × 100 + 순번, 기간 안 기사 수는 내림차순 */
	private static FlowMaterial flowWithEvents(long head, int events, int titleLength) {
		List<EventLine> lines = IntStream.range(0, events)
			.mapToObj(i -> new EventLine(new EventId(head * 100 + i), "제목" + "타".repeat(titleLength),
					LocalDate.of(2026, 11, 17), events - i, true))
			.toList();
		return new FlowMaterial(new StorylineId(head), Set.of(new StorylineId(head)), null, StorylineState.ACTIVE, false,
				new Count(10, null), lines);
	}

	private static StorylinesSection storylines(ReportInput input) {
		return (StorylinesSection) input.sections()
			.stream()
			.filter(section -> section.kind() == ReportSectionKind.STORYLINES)
			.findFirst()
			.orElseThrow();
	}

	private static InterestsSection interests(ReportInput input) {
		return (InterestsSection) input.sections()
			.stream()
			.filter(section -> section.kind() == ReportSectionKind.INTERESTS)
			.findFirst()
			.orElseThrow();
	}

}
