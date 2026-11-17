package net.yozmnews.storyline.domain.linking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 잇기 판정 — 위 기준 이상은 묻지 않고 가장 높은 하나, 띠 안은 물은 확률로, 붙이기는 고른 것이 지금 소속이 아닐 때만, 짝은 소속 없는
 * 이웃에서, 다시 넣기는 아래 기준 이상이고 소속 없는 이웃. 띠가 빈 기준은 0.65 · 0.65, 띠는 [0.84, 0.90) · 확률 기준 0.50.
 * <p>
 * 지키는 규칙: storyline-R10 · storyline-R11 · storyline-R12 · storyline-R36
 */
class LinkingRuleTests {

	private static final LinkingCriteria FLAT = new LinkingCriteria(Duration.ofDays(14), 0.65, 0.65, 0.50);

	private static final LinkingCriteria BANDED = new LinkingCriteria(Duration.ofDays(14), 0.84, 0.90, 0.50);

	@Test
	void attachTakesTheBestWhenItIsNotAlreadyAMembership() {
		List<Scored> storylines = List.of(scored(1, 0.70), scored(2, 0.80), scored(3, 0.60));

		assertThat(LinkingRule.attach(storylines, Set.of(), Map.of(), FLAT))
			.isEqualTo(new LinkingRule.Choice.Take(scored(2, 0.80), null));
		// 가장 높은 것이 이미 소속이면 다음 것을 붙이지 않는다
		assertThat(LinkingRule.attach(storylines, Set.of(2L), Map.of(), FLAT)).isEqualTo(new LinkingRule.Choice.None());
		assertThat(LinkingRule.attach(storylines, Set.of(1L), Map.of(), FLAT))
			.isEqualTo(new LinkingRule.Choice.Take(scored(2, 0.80), null));
	}

	@Test
	void attachBreaksTiesBySmallerIdAndNeedsTheThreshold() {
		assertThat(LinkingRule.attach(List.of(scored(5, 0.80), scored(4, 0.80)), Set.of(), Map.of(), FLAT))
			.isEqualTo(new LinkingRule.Choice.Take(scored(4, 0.80), null));
		assertThat(LinkingRule.attach(List.of(scored(1, 0.64), scored(2, Double.NaN)), Set.of(), Map.of(), FLAT))
			.isEqualTo(new LinkingRule.Choice.None());
		// 경계는 포함한다
		assertThat(LinkingRule.attach(List.of(scored(1, 0.65)), Set.of(), Map.of(), FLAT))
			.isEqualTo(new LinkingRule.Choice.Take(scored(1, 0.65), null));
	}

	@Test
	void pairIsTheBestUnaffiliatedNeighbour() {
		List<Scored> neighbours = List.of(scored(11, 0.90), scored(12, 0.70), scored(13, 0.75));

		assertThat(LinkingRule.pair(neighbours, Set.of(11L), Map.of(), FLAT))
			.isEqualTo(new LinkingRule.Choice.Take(scored(13, 0.75), null));
		assertThat(LinkingRule.pair(neighbours, Set.of(11L, 12L, 13L), Map.of(), FLAT))
			.isEqualTo(new LinkingRule.Choice.None());
		assertThat(LinkingRule.pair(List.of(scored(14, 0.60)), Set.of(), Map.of(), FLAT))
			.isEqualTo(new LinkingRule.Choice.None());
	}

	@Test
	void requeueIsUnaffiliatedNeighboursFromTheLowerThresholdInIdOrder() {
		// N1 0.70(소속 없음) · N2 0.66(소속 있음) · N3 0.60(소속 없음) · P 0.80(소속 없음 — 짝이라 뺀다) · 점수 NaN(소속 없음)
		List<Scored> neighbours = List.of(scored(31, 0.70), scored(32, 0.66), scored(33, 0.60), scored(30, 0.80),
				scored(34, Double.NaN));

		assertThat(LinkingRule.requeue(neighbours, Set.of(32L), Set.of(30L), FLAT)).containsExactly(31L);
		assertThat(LinkingRule.requeue(neighbours, Set.of(32L), Set.of(), FLAT)).containsExactly(30L, 31L);
		// 띠가 있으면 문턱은 아래 기준이다 — 0.84 이상, 위 기준 0.90 미만도 든다
		assertThat(LinkingRule.requeue(List.of(scored(41, 0.84), scored(42, 0.83), scored(43, 0.86)), Set.of(),
				Set.of(), BANDED))
			.containsExactly(41L, 43L);
	}

	@Test
	void aboveTheUpperThresholdTakesWithoutAsking() {
		List<Scored> storylines = List.of(scored(1, 0.90), scored(2, 0.86));

		assertThat(LinkingRule.attach(storylines, Set.of(), Map.of(), BANDED))
			.isEqualTo(new LinkingRule.Choice.Take(scored(1, 0.90), null));
	}

	@Test
	void asksTheBandCandidatesWithoutAUsableRecordInScoreOrder() {
		List<Scored> storylines = List.of(scored(1, 0.84), scored(2, 0.86), scored(3, 0.83), scored(4, 0.89));

		assertThat(LinkingRule.attach(storylines, Set.of(), Map.of(), BANDED))
			.isEqualTo(new LinkingRule.Choice.Ask(List.of(scored(4, 0.89), scored(2, 0.86), scored(1, 0.84))));
		// 기록이 있는 후보는 묻지 않는다
		assertThat(LinkingRule.attach(storylines, Set.of(), Map.of(4L, answered(0.71, 9)), BANDED))
			.isEqualTo(new LinkingRule.Choice.Ask(List.of(scored(2, 0.86), scored(1, 0.84))));
	}

	@Test
	void asksAtMostFiveBandCandidates() {
		List<Scored> storylines = List.of(scored(1, 0.85), scored(2, 0.86), scored(3, 0.87), scored(4, 0.88),
				scored(5, 0.89), scored(6, 0.84));

		assertThat(LinkingRule.attach(storylines, Set.of(), Map.of(), BANDED))
			.isEqualTo(new LinkingRule.Choice.Ask(
					List.of(scored(5, 0.89), scored(4, 0.88), scored(3, 0.87), scored(2, 0.86), scored(1, 0.85))));
	}

	@Test
	void takesTheHighestProbabilityAtOrAboveTheProbabilityThreshold() {
		List<Scored> storylines = List.of(scored(1, 0.89), scored(2, 0.86), scored(3, 0.84));
		Map<Long, Asked> asked = Map.of(1L, answered(0.49, 11), 2L, answered(0.71, 12), 3L, answered(0.50, 13));

		assertThat(LinkingRule.attach(storylines, Set.of(), asked, BANDED))
			.isEqualTo(new LinkingRule.Choice.Take(scored(2, 0.86), answered(0.71, 12)));
		// 점수가 아니라 확률로 고른다 — 확률 기준을 넘은 둘 가운데 점수가 낮은 3 의 확률이 더 높다
		Map<Long, Asked> higher = Map.of(1L, answered(0.49, 11), 2L, answered(0.60, 12), 3L, answered(0.80, 13));
		assertThat(LinkingRule.attach(storylines, Set.of(), higher, BANDED))
			.isEqualTo(new LinkingRule.Choice.Take(scored(3, 0.84), answered(0.80, 13)));
		// 경계는 포함한다 — 확률 기준과 같은 0.50 만 남으면 그것
		Map<Long, Asked> low = Map.of(1L, answered(0.49, 11), 2L, answered(0.30, 12), 3L, answered(0.50, 13));
		assertThat(LinkingRule.attach(storylines, Set.of(), low, BANDED))
			.isEqualTo(new LinkingRule.Choice.Take(scored(3, 0.84), answered(0.50, 13)));
	}

	@Test
	void takesNothingWhenNoProbabilityReachesTheThreshold() {
		List<Scored> storylines = List.of(scored(1, 0.89), scored(2, 0.86));

		assertThat(LinkingRule.attach(storylines, Set.of(), Map.of(1L, answered(0.49, 11), 2L, answered(0.10, 12)),
				BANDED))
			.isEqualTo(new LinkingRule.Choice.None());
	}

	@Test
	void breaksAProbabilityTieByScoreThenId() {
		List<Scored> storylines = List.of(scored(1, 0.85), scored(2, 0.88), scored(3, 0.88));
		Map<Long, Asked> asked = Map.of(1L, answered(0.71, 11), 2L, answered(0.71, 12), 3L, answered(0.71, 13));

		assertThat(LinkingRule.attach(storylines, Set.of(), asked, BANDED))
			.isEqualTo(new LinkingRule.Choice.Take(scored(2, 0.88), answered(0.71, 12)));
	}

	@Test
	void anUnansweredRecordCannotBeTaken() {
		List<Scored> storylines = List.of(scored(1, 0.89), scored(2, 0.86));

		assertThat(LinkingRule.attach(storylines, Set.of(), Map.of(1L, Asked.UNANSWERED, 2L, answered(0.71, 12)),
				BANDED))
			.isEqualTo(new LinkingRule.Choice.Take(scored(2, 0.86), answered(0.71, 12)));
		assertThat(LinkingRule.attach(storylines, Set.of(), Map.of(1L, Asked.UNANSWERED, 2L, Asked.UNANSWERED),
				BANDED))
			.isEqualTo(new LinkingRule.Choice.None());
	}

	@Test
	void aCurrentMembershipInTheBandIsAskedButNotTakenAgain() {
		List<Scored> storylines = List.of(scored(1, 0.89), scored(2, 0.86));

		assertThat(LinkingRule.attach(storylines, Set.of(1L), Map.of(), BANDED))
			.isEqualTo(new LinkingRule.Choice.Ask(List.of(scored(1, 0.89), scored(2, 0.86))));
		// 지금 소속이 가장 높은 확률이면 아무것도 하지 않는다 — 다른 후보가 받아들여지면 거기에 더 붙는다
		assertThat(LinkingRule.attach(storylines, Set.of(1L), Map.of(1L, answered(0.90, 11), 2L, answered(0.71, 12)),
				BANDED))
			.isEqualTo(new LinkingRule.Choice.None());
		assertThat(LinkingRule.attach(storylines, Set.of(1L), Map.of(1L, answered(0.20, 11), 2L, answered(0.71, 12)),
				BANDED))
			.isEqualTo(new LinkingRule.Choice.Take(scored(2, 0.86), answered(0.71, 12)));
	}

	@Test
	void pairAsksOnlyUnaffiliatedBandNeighbours() {
		List<Scored> neighbours = List.of(scored(11, 0.89), scored(12, 0.86));

		assertThat(LinkingRule.pair(neighbours, Set.of(11L), Map.of(), BANDED))
			.isEqualTo(new LinkingRule.Choice.Ask(List.of(scored(12, 0.86))));
		assertThat(LinkingRule.pair(neighbours, Set.of(11L), Map.of(12L, answered(0.50, 21)), BANDED))
			.isEqualTo(new LinkingRule.Choice.Take(scored(12, 0.86), answered(0.50, 21)));
	}

	@Test
	void aBandlessCriteriaNeverAsks() {
		assertThat(LinkingRule.attach(List.of(scored(1, 0.85)), Set.of(), Map.of(),
				new LinkingCriteria(Duration.ofDays(14), 0.86, 0.86, 0.50)))
			.isEqualTo(new LinkingRule.Choice.None());
	}

	@Test
	void aNaNScoreIsNeitherTakenNorAsked() {
		assertThat(LinkingRule.attach(List.of(scored(1, Double.NaN)), Set.of(), Map.of(), BANDED))
			.isEqualTo(new LinkingRule.Choice.None());
	}

	private static Scored scored(long id, double score) {
		return new Scored(id, new Score(score, 0.9, 0.5));
	}

	private static Asked answered(double probability, long judgement) {
		return new Asked(probability, judgement);
	}

}
