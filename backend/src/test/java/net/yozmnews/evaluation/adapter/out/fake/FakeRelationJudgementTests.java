package net.yozmnews.evaluation.adapter.out.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.yozmnews.evaluation.EvaluatorFailureKind;
import net.yozmnews.evaluation.RelationAnswer;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.FlowText;
import net.yozmnews.evaluation.domain.evaluator.HappeningText;
import net.yozmnews.evaluation.domain.evaluator.Relation;
import net.yozmnews.evaluation.domain.evaluator.RelationInput;
import net.yozmnews.evaluation.domain.evaluator.RelationWording;
import net.yozmnews.evaluation.domain.evaluator.RereadFailure;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 고정 규칙 평가기의 관계 판정 — 조각 겹침으로 답을 고르고, 서술 제목끼리의 겹침으로 한 사안 확률을 낸다. 같은 입력에 같은 결과다.
 * <p>
 * 지키는 규칙: evaluation-R48 · evaluation-R50 (로컬 대체물 — docs/specs/evaluation.md §4)
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
	void rereadsTheRawResponseItWrote() {
		Relation judged = (Relation) this.evaluator
			.judgeRelation(input(List.of("서울 시장 선거 개표", "서울 시장 당선 확정"), List.of("서울 시장")));

		// 입력 토큰은 원본 응답에 없어 0 이다 — 나머지는 판정 때와 같다
		assertThat(this.evaluator.rereadRelation("fake", judged.rawResponse()))
			.isEqualTo(new Relation(judged.probabilities(), judged.firstOneMatter(), judged.secondOneMatter(), "fake",
					"fake-rules-2", 0, judged.rawResponse()));
	}

	@Test
	void rereadsAHandWrittenRawResponse() {
		String raw = "{\"evaluator\":\"fake\",\"modelVersion\":\"fake-rules-1\",\"relation\":{\"FIRST_LED_TO_SECOND\":0.05,"
				+ "\"FIRST_PART_OF_SECOND\":0.05,\"NONE\":0.8,\"SECOND_LED_TO_FIRST\":0.05,\"SECOND_PART_OF_FIRST\":0.05},"
				+ "\"oneMatter\":[0.3,0.9]}";

		assertThat(this.evaluator.rereadRelation("fake", raw)).isEqualTo(new Relation(
				Map.of(RelationAnswer.NONE, 0.8, RelationAnswer.SECOND_PART_OF_FIRST, 0.05,
						RelationAnswer.FIRST_PART_OF_SECOND, 0.05, RelationAnswer.FIRST_LED_TO_SECOND, 0.05,
						RelationAnswer.SECOND_LED_TO_FIRST, 0.05),
				0.3, 0.9, "fake", "fake-rules-1", 0, raw));
	}

	@Test
	void doesNotReadARawResponseOfAnotherEvaluator() {
		assertThat(this.evaluator.rereadRelation("jev", "{}")).isEqualTo(
				new RereadFailure(RereadFailure.Kind.OTHER_EVALUATOR, "Fake 가 남긴 원본 응답이 아니다 — 평가기 jev"));
	}

	@Test
	void cannotReadABrokenRawResponseAndLeavesItsTextOutOfTheMessage() {
		String outOfRange = "{\"modelVersion\":\"fake-rules-2\",\"relation\":{\"FIRST_LED_TO_SECOND\":0.1,"
				+ "\"FIRST_PART_OF_SECOND\":0.1,\"NONE\":1.7,\"SECOND_LED_TO_FIRST\":0.1,\"SECOND_PART_OF_FIRST\":0.1},"
				+ "\"oneMatter\":[0.5,0.5]}";
		String oneMatterMissing = outOfRange.replace("1.7", "0.6").replace(",\"oneMatter\":[0.5,0.5]", "");

		assertThat(this.evaluator.rereadRelation("fake", outOfRange)).isEqualTo(new RereadFailure(
				RereadFailure.Kind.UNREADABLE, "Fake 원본 응답을 읽을 수 없다 — 확률이 0 ~ 1 이 아니다 — relation.NONE"));
		assertThat(this.evaluator.rereadRelation("fake", oneMatterMissing)).isEqualTo(new RereadFailure(
				RereadFailure.Kind.UNREADABLE, "Fake 원본 응답을 읽을 수 없다 — oneMatter 가 두 값의 배열이 아니다"));
		assertThat(this.evaluator.rereadRelation("fake", "not json")).isEqualTo(
				new RereadFailure(RereadFailure.Kind.UNREADABLE, "Fake 원본 응답을 읽을 수 없다 — JSON 이 아니다"));
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
