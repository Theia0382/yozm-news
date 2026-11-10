package net.yozmnews.storyline.domain.linking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 잇기 판정 — 붙이기는 가장 높은 하나가 지금 소속이 아닐 때만, 짝은 점수 순으로 소속 없는 첫 사건, 다시 넣기는 기준 이상이고 소속 없는 이웃.
 * 기준 0.65.
 * <p>
 * 지키는 규칙: storyline-R10 · storyline-R11 · storyline-R12
 */
class LinkingRuleTests {

	private static final double THRESHOLD = 0.65;

	@Test
	void attachTakesTheBestWhenItIsNotAlreadyAMembership() {
		List<Scored> storylines = List.of(scored(1, 0.70), scored(2, 0.80), scored(3, 0.60));

		assertThat(LinkingRule.attach(storylines, Set.of(), THRESHOLD)).map(Scored::id).hasValue(2L);
		// 가장 높은 것이 이미 소속이면 다음 것을 붙이지 않는다
		assertThat(LinkingRule.attach(storylines, Set.of(2L), THRESHOLD)).isEmpty();
		assertThat(LinkingRule.attach(storylines, Set.of(1L), THRESHOLD)).map(Scored::id).hasValue(2L);
	}

	@Test
	void attachBreaksTiesBySmallerIdAndNeedsTheThreshold() {
		assertThat(LinkingRule.attach(List.of(scored(5, 0.80), scored(4, 0.80)), Set.of(), THRESHOLD))
			.map(Scored::id)
			.hasValue(4L);
		assertThat(LinkingRule.attach(List.of(scored(1, 0.64), scored(2, Double.NaN)), Set.of(), THRESHOLD)).isEmpty();
		// 경계는 포함한다
		assertThat(LinkingRule.attach(List.of(scored(1, 0.65)), Set.of(), THRESHOLD)).map(Scored::id).hasValue(1L);
	}

	@Test
	void pairIsTheFirstUnaffiliatedNeighbourInScoreOrder() {
		List<Scored> neighbours = List.of(scored(11, 0.90), scored(12, 0.70), scored(13, 0.75));

		assertThat(LinkingRule.pair(neighbours, Set.of(11L), THRESHOLD)).map(Scored::id).hasValue(13L);
		assertThat(LinkingRule.pair(neighbours, Set.of(11L, 12L, 13L), THRESHOLD)).isEmpty();
		assertThat(LinkingRule.pair(List.of(scored(14, 0.60)), Set.of(), THRESHOLD)).isEmpty();
	}

	@Test
	void requeueIsUnaffiliatedNeighboursAboveTheThresholdInIdOrder() {
		// N1 0.70(소속 없음) · N2 0.66(소속 있음) · N3 0.60(소속 없음) · P 0.80(소속 없음 — 짝이라 뺀다) · 점수 NaN(소속 없음)
		List<Scored> neighbours = List.of(scored(31, 0.70), scored(32, 0.66), scored(33, 0.60), scored(30, 0.80),
				scored(34, Double.NaN));

		assertThat(LinkingRule.requeue(neighbours, Set.of(32L), Set.of(30L), THRESHOLD)).containsExactly(31L);
		assertThat(LinkingRule.requeue(neighbours, Set.of(32L), Set.of(), THRESHOLD)).containsExactly(30L, 31L);
	}

	private static Scored scored(long id, double score) {
		return new Scored(id, new Score(score, 0.9, 0.5));
	}

}
