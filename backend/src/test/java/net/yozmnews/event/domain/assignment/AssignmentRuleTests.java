package net.yozmnews.event.domain.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import net.yozmnews.event.AssignmentCriteria;
import net.yozmnews.event.DecidedBy;
import net.yozmnews.event.domain.assignment.Decision.Ask;
import net.yozmnews.event.domain.assignment.Decision.Attach;
import net.yozmnews.event.domain.assignment.Decision.Keep;
import net.yozmnews.event.domain.assignment.Decision.Open;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * 배정 판정 — 위 기준 · 띠 · 확률 기준의 경계(포함), 같은 값의 순서, 판정의 때마다 확률 없는 띠 후보의 처리. 기준은 위 0.80 · 아래
 * 0.55 · 저장 0.40 · 확률 0.50 이다.
 * <p>
 * 지키는 규칙: event-R11 · event-R18
 */
class AssignmentRuleTests {

	private static final AssignmentCriteria CRITERIA = new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.55, 0.40,
			0.50);

	@Test
	void theUpperBoundaryAttachesByFormula() {
		assertThat(decide(DecisionPhase.FIRST, candidate(7, 0.80), candidate(3, 0.60)))
			.isEqualTo(new Attach(7, 0.80, DecidedBy.FORMULA));
	}

	@Test
	void justBelowTheUpperBoundaryIsInTheBand() {
		assertThat(decide(DecisionPhase.FIRST, candidate(7, 0.7999))).isEqualTo(new Ask(List.of(7L)));
	}

	@Test
	void theLowerBoundaryIsInTheBandAndJustBelowOpensByFormula() {
		assertThat(decide(DecisionPhase.FIRST, candidate(7, 0.55))).isEqualTo(new Ask(List.of(7L)));
		assertThat(decide(DecisionPhase.FIRST, candidate(7, 0.5499))).isEqualTo(new Open(0.5499, DecidedBy.FORMULA));
	}

	@Test
	void theBandIncludesTheLowerBoundaryAndExcludesTheUpper() {
		assertThat(AssignmentRule.inBand(0.55, CRITERIA)).isTrue();
		assertThat(AssignmentRule.inBand(0.7999, CRITERIA)).isTrue();
		assertThat(AssignmentRule.inBand(0.80, CRITERIA)).isFalse();
		assertThat(AssignmentRule.inBand(0.5499, CRITERIA)).isFalse();
	}

	@Test
	void noCandidateOpensByFormulaWithoutAScore() {
		assertThat(decide(DecisionPhase.FIRST)).isEqualTo(new Open(null, DecidedBy.FORMULA));
	}

	@Test
	void equalTopScoresAttachToTheSmallerEventId() {
		assertThat(decide(DecisionPhase.FIRST, candidate(9, 0.85), candidate(4, 0.85), candidate(2, 0.81)))
			.isEqualTo(new Attach(4, 0.85, DecidedBy.FORMULA));
	}

	@Test
	void theAskListsAtMostFiveBandCandidatesByScoreThenEventId() {
		assertThat(decide(DecisionPhase.FIRST, candidate(1, 0.56), candidate(2, 0.70), candidate(3, 0.60),
				candidate(4, 0.70), candidate(5, 0.79), candidate(6, 0.58), candidate(8, 0.45)))
			.isEqualTo(new Ask(List.of(5L, 2L, 4L, 3L, 6L)));
	}

	@Test
	void theProbabilityThresholdIsInclusive() {
		assertThat(AssignmentRule.passes(0.50, CRITERIA)).isTrue();
		assertThat(AssignmentRule.passes(0.4999, CRITERIA)).isFalse();
	}

	@Test
	void theProbabilityBoundaryAttachesByEvaluator() {
		assertThat(decide(DecisionPhase.AFTER_JUDGEMENT, judged(7, 0.60, 0.50), judged(3, 0.70, 0.4999)))
			.isEqualTo(new Attach(7, 0.60, DecidedBy.EVALUATOR));
	}

	@Test
	void theHighestProbabilityWinsThenTheHigherScore() {
		assertThat(decide(DecisionPhase.AFTER_JUDGEMENT, judged(7, 0.60, 0.90), judged(3, 0.75, 0.70)))
			.isEqualTo(new Attach(7, 0.60, DecidedBy.EVALUATOR));
		assertThat(decide(DecisionPhase.AFTER_JUDGEMENT, judged(7, 0.60, 0.70), judged(3, 0.75, 0.70)))
			.isEqualTo(new Attach(3, 0.75, DecidedBy.EVALUATOR));
	}

	@Test
	void probabilitiesAllBelowTheThresholdOpenByEvaluator() {
		assertThat(decide(DecisionPhase.AFTER_JUDGEMENT, judged(7, 0.60, 0.4999), judged(3, 0.75, 0.10)))
			.isEqualTo(new Open(0.75, DecidedBy.EVALUATOR));
	}

	@Test
	void afterJudgementAnUnaskedBandCandidateCannotBeAttached() {
		assertThat(decide(DecisionPhase.AFTER_JUDGEMENT, judged(7, 0.60, 0.30), candidate(3, 0.78)))
			.isEqualTo(new Open(0.78, DecidedBy.EVALUATOR));
		assertThat(decide(DecisionPhase.AFTER_JUDGEMENT, candidate(3, 0.78)))
			.isEqualTo(new Open(0.78, DecidedBy.FORMULA));
	}

	@Test
	void aRejudgementKeepsTheAssignmentWhenABandCandidateHasNoProbability() {
		assertThat(decide(DecisionPhase.REJUDGE, judged(7, 0.60, 0.30), candidate(3, 0.78))).isEqualTo(new Keep());
	}

	@Test
	void aRejudgementStillAttachesWhenAJudgedCandidatePasses() {
		assertThat(decide(DecisionPhase.REJUDGE, judged(7, 0.60, 0.70), candidate(3, 0.78)))
			.isEqualTo(new Attach(7, 0.60, DecidedBy.EVALUATOR));
		assertThat(decide(DecisionPhase.REJUDGE, judged(7, 0.81, 0.10), candidate(3, 0.78)))
			.isEqualTo(new Attach(7, 0.81, DecidedBy.FORMULA));
	}

	@Test
	void probabilitiesOutsideTheBandAreIgnored() {
		// 아래 기준 미만 후보의 확률은 띠 밖이라 붙이지도, 판정이 정한 것으로 세지도 않는다
		assertThat(decide(DecisionPhase.REJUDGE, judged(7, 0.45, 0.95))).isEqualTo(new Open(0.45, DecidedBy.FORMULA));
	}

	private static Decision decide(DecisionPhase phase, StoredCandidate... candidates) {
		return AssignmentRule.decide(List.of(candidates), CRITERIA, phase);
	}

	private static StoredCandidate candidate(long eventId, double score) {
		return judged(eventId, score, null);
	}

	private static StoredCandidate judged(long eventId, double score, @Nullable Double probability) {
		return new StoredCandidate(eventId, score, probability);
	}

}
