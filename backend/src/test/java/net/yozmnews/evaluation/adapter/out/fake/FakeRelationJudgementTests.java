package net.yozmnews.evaluation.adapter.out.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import net.yozmnews.evaluation.EvaluatorFailureKind;
import net.yozmnews.evaluation.RelationAnswer;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.FlowText;
import net.yozmnews.evaluation.domain.evaluator.HappeningText;
import net.yozmnews.evaluation.domain.evaluator.Relation;
import net.yozmnews.evaluation.domain.evaluator.RelationInput;
import net.yozmnews.evaluation.domain.evaluator.RelationWording;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 고정 규칙 평가기의 관계 판정 — 조각 겹침으로 답을 고르고, 서술 제목끼리의 겹침으로 한 사안 확률을 낸다. 같은 입력에 같은 결과다.
 * <p>
 * 지키는 규칙: evaluation-R48 (로컬 대체물 — docs/specs/evaluation.md §4)
 */
class FakeRelationJudgementTests {

	private static final Instant DAY = Instant.parse("2026-11-01T03:00:00Z");

	private final FakeArticleEvaluator evaluator = new FakeArticleEvaluator();

	@Test
	void theFlowWithFewerDescriptionsIsPartOfTheOtherWhenTheirPiecesOverlap() {
		// 조각 A = {서울, 시장, 선거, 개표, 당선, 확정}, B = {서울, 시장} — 겹침 2 ÷ 2 = 1 → 0.5 + 0.4 = 0.9
		Relation relation = (Relation) this.evaluator
			.judgeRelation(input(List.of("서울 시장 선거 개표", "서울 시장 당선 확정"), List.of("서울 시장")));

		assertThat(relation.probabilities().get(RelationAnswer.SECOND_PART_OF_FIRST)).isCloseTo(0.9, within(1e-12));
		assertOthers(relation, RelationAnswer.SECOND_PART_OF_FIRST, 0.025);
		// 두 제목이 서로 조각을 함께 가진다 → 0.1 + 0.8 × 2 ÷ 2, 제목 하나 → 0.9
		assertThat(relation.firstOneMatter()).isCloseTo(0.9, within(1e-12));
		assertThat(relation.secondOneMatter()).isEqualTo(0.9);
		assertThat(relation.evaluator()).isEqualTo("fake");
		assertThat(relation.modelVersion()).isEqualTo("fake-rules-2");
		assertThat(JsonMapper.builder().build().readTree(relation.rawResponse()).get("relation").size()).isEqualTo(5);
	}

	@Test
	void theFirstFlowIsThePartWhenItHasFewerDescriptions() {
		// 조각 A = {서울, 시장, 취임}, B = {서울, 시장, 선거, 개표, 부산, 항만, 파업} — 겹침 2 ÷ min(3, 7) → 0.5 + 0.4 × 2 ÷ 3
		Relation relation = (Relation) this.evaluator
			.judgeRelation(input(List.of("서울 시장 취임"), List.of("서울 시장 선거 개표", "부산 항만 파업")));

		assertThat(relation.probabilities().get(RelationAnswer.FIRST_PART_OF_SECOND))
			.isCloseTo(0.7666666666666667, within(1e-12));
		// 둘째 흐름의 두 제목은 조각을 함께 갖지 않는다 → 0.1
		assertThat(relation.secondOneMatter()).isCloseTo(0.1, within(1e-12));
	}

	@Test
	void answersNoneWithoutSharedPieces() {
		Relation relation = (Relation) this.evaluator
			.judgeRelation(input(List.of("서울 시장 선거"), List.of("부산 항만 파업")));

		assertThat(relation.probabilities().get(RelationAnswer.NONE)).isEqualTo(0.9);
		assertOthers(relation, RelationAnswer.NONE, 0.025);
	}

	@Test
	void givesTheSameResultForTheSameInput() {
		RelationInput input = input(List.of("서울 시장 선거 개표", "서울 시장 당선 확정"), List.of("서울 시장"));

		assertThat(this.evaluator.judgeRelation(input)).isEqualTo(this.evaluator.judgeRelation(input));
		// 입력 토큰은 state · 관계 질문 · 선택지 다섯 · 한 사안 질문 둘의 코드 포인트 합의 절반(올림)이다 — 0 보다 크다
		assertThat(((Relation) this.evaluator.judgeRelation(input)).inputTokens()).isGreaterThan(400);
	}

	@Test
	void failsWithTheKindOfTheMarkerInTheFirstTitleOfTheFirstFlow() {
		assertThat(this.evaluator.judgeRelation(input(List.of("[fake:transient] 서울 시장"), List.of("서울 시장"))))
			.isEqualTo(new EvaluationFailure(EvaluatorFailureKind.TRANSIENT, "Fake 평가기 표지에 따른 실패"));
		// 다른 자리의 표지는 보지 않는다
		assertThat(this.evaluator
			.judgeRelation(input(List.of("서울 시장", "[fake:transient] 선거"), List.of("[fake:article-rejected] 서울"))))
			.isInstanceOf(Relation.class);
	}

	private static void assertOthers(Relation relation, RelationAnswer chosen, double expected) {
		Arrays.stream(RelationAnswer.values())
			.filter(answer -> answer != chosen)
			.forEach(answer -> assertThat(relation.probabilities().get(answer)).isCloseTo(expected, within(1e-12)));
	}

	private static RelationInput input(List<String> first, List<String> second) {
		return new RelationInput(flow(first), flow(second), RelationWording.CURRENT);
	}

	private static FlowText flow(List<String> titles) {
		return new FlowText(titles.size(), DAY, DAY,
				titles.stream().map(title -> new HappeningText(DAY, title, null)).toList());
	}

}
