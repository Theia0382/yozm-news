package net.yozmnews.evaluation.domain.relation;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.FlowDescriptor;
import net.yozmnews.evaluation.FlowHappening;
import net.yozmnews.evaluation.RelationAnswer;
import net.yozmnews.evaluation.RelationQuery;
import net.yozmnews.evaluation.domain.evaluator.FlowText;
import net.yozmnews.evaluation.domain.evaluator.HappeningText;
import net.yozmnews.evaluation.domain.evaluator.Relation;
import net.yozmnews.evaluation.domain.evaluator.RelationInput;
import net.yozmnews.evaluation.domain.evaluator.RelationWording;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * 물을 서술 고르기와 결과 대조 — 지워진 기사 · 빈 제목의 서술을 빼고, 표시된 사건에만 요약문을 싣고, 답 다섯을 본다.
 * <p>
 * 지키는 규칙: evaluation-R49
 */
class RelationPlanTests {

	private static final Instant T1 = Instant.parse("2026-11-01T00:00:00Z");

	private static final Instant T2 = Instant.parse("2026-11-02T00:00:00Z");

	private static final Instant T3 = Instant.parse("2026-11-03T00:00:00Z");

	private final Map<ArticleId, ArticleContent> found = new HashMap<>();

	@Test
	void asksTheDescriptionsInRequestOrderWithSummariesOnlyWhereMarked() {
		article(1, "특검법 통과", "재석 과반");
		article(2, "거부권 건의", "야당 반발");
		article(3, "특검 후보 추천", null);
		RelationQuery query = new RelationQuery(
				new FlowDescriptor(7, 12, T1, T3, List.of(happening(1, T1, true), happening(2, T2, false))),
				new FlowDescriptor(9, 4, T2, T3, List.of(happening(3, T2, true))));

		RelationPlan.Plan plan = RelationPlan.of(query, this.found, RelationWording.CURRENT);

		assertThat(plan).isEqualTo(new RelationPlan.Plan.Ask(
				new RelationInput(
						new FlowText(12, T1, T3,
								List.of(new HappeningText(T1, "특검법 통과", "재석 과반"),
										new HappeningText(T2, "거부권 건의", null))),
						// 표시됐지만 요약문이 없는 기사 — 싣지 않고, 기록에도 싣지 않았다고 남긴다
						new FlowText(4, T2, T3, List.of(new HappeningText(T2, "특검 후보 추천", null))),
						RelationWording.CURRENT),
				List.of(new RelationRecord.Described(new ArticleId(1), true),
						new RelationRecord.Described(new ArticleId(2), false)),
				List.of(new RelationRecord.Described(new ArticleId(3), false))));
	}

	@Test
	void leavesOutDeletedArticlesAndBlankTitles() {
		article(1, "특검법 통과", "  ");
		article(2, "   ", "제목이 빈 기사");
		article(3, "특검 후보 추천", null);
		RelationQuery query = new RelationQuery(
				new FlowDescriptor(7, 12, T1, T3,
						List.of(happening(1, T1, true), happening(2, T2, true), happening(404, T3, false))),
				new FlowDescriptor(9, 4, T2, T3, List.of(happening(3, T2, false))));

		RelationPlan.Plan.Ask ask = (RelationPlan.Plan.Ask) RelationPlan.of(query, this.found,
				RelationWording.CURRENT);

		// 흐름의 사건 수 · 시각은 요청 그대로다 — 서술이 줄어도 바뀌지 않는다
		assertThat(ask.input().first())
			.isEqualTo(new FlowText(12, T1, T3, List.of(new HappeningText(T1, "특검법 통과", null))));
		assertThat(ask.first()).containsExactly(new RelationRecord.Described(new ArticleId(1), false));
	}

	@Test
	void asksNothingWhenEitherFlowHasNoDescriptionLeft() {
		article(1, "특검법 통과", null);
		RelationQuery firstOnly = new RelationQuery(
				new FlowDescriptor(7, 12, T1, T3, List.of(happening(1, T1, false))),
				new FlowDescriptor(9, 4, T2, T3, List.of(happening(404, T2, false))));
		RelationQuery secondOnly = new RelationQuery(
				new FlowDescriptor(9, 4, T2, T3, List.of(happening(404, T2, false))),
				new FlowDescriptor(7, 12, T1, T3, List.of(happening(1, T1, false))));

		assertThat(RelationPlan.of(firstOnly, this.found, RelationWording.CURRENT))
			.isEqualTo(new RelationPlan.Plan.NothingToAsk());
		assertThat(RelationPlan.of(secondOnly, this.found, RelationWording.CURRENT))
			.isEqualTo(new RelationPlan.Plan.NothingToAsk());
	}

	@Test
	void theResultMustCarryExactlyTheFiveAnswersAndAStorableResponse() {
		Map<RelationAnswer, Double> all = Map.of(RelationAnswer.NONE, 0.6, RelationAnswer.SECOND_PART_OF_FIRST, 0.1,
				RelationAnswer.FIRST_PART_OF_SECOND, 0.1, RelationAnswer.FIRST_LED_TO_SECOND, 0.1,
				RelationAnswer.SECOND_LED_TO_FIRST, 0.1);
		Map<RelationAnswer, Double> three = Map.of(RelationAnswer.NONE, 0.6, RelationAnswer.SECOND_PART_OF_FIRST, 0.1,
				RelationAnswer.FIRST_PART_OF_SECOND, 0.1);

		assertThat(RelationCheck.mismatch(new Relation(all, 0.5, 0.5, "jev", "jev-1", 10, "{}"))).isEmpty();
		assertThat(RelationCheck.mismatch(new Relation(three, 0.5, 0.5, "jev", "jev-1", 10, "{}")))
			.contains("답이 선택지 다섯과 다르다 — 빠진 답 [FIRST_LED_TO_SECOND, SECOND_LED_TO_FIRST], 모르는 답 []");
		assertThat(RelationCheck.mismatch(new Relation(all, 0.5, 0.5, "jev", "jev-1", 10, "not json")))
			.contains("원본 응답이 JSON 이 아니다");
		assertThat(RelationCheck.mismatch(new Relation(all, 0.5, 0.5, "jev", "jev-1", 10, "{\"a\":\"\\u0000\"}")))
			.contains("원본 응답에 문자 U+0000 이 있다");
	}

	private void article(long id, String title, @Nullable String summary) {
		this.found.put(new ArticleId(id), new ArticleContent(new ArticleId(id), title, summary,
				URI.create("https://plan.example/" + id), T1, List.of()));
	}

	private static FlowHappening happening(long articleId, Instant at, boolean summarized) {
		return new FlowHappening(new ArticleId(articleId), at, summarized);
	}

}
