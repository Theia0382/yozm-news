package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.yozmnews.digest.domain.interest.InterestWeights;
import net.yozmnews.digest.domain.interest.StorylineInterests;
import net.yozmnews.digest.domain.interest.TopicInterests;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigEvent;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigRow;
import net.yozmnews.event.EventId;
import net.yozmnews.storyline.StorylineId;
import org.junit.jupiter.api.Test;

/**
 * 큰 판정 행의 순서 — 보도 강도 × 후보 사건 관심 가중치의 평균, 내림차순, 같으면 받은 순서. 기대값은 손으로 셈한 리터럴이다.
 * <p>
 * 지키는 규칙: digest-R19
 */
class BigRowOrderTests {

	/** 주제 가중치 — a 0.8 · b 1.2 · n 1.0(신호 없음) */
	private static final InterestWeights WEIGHTS = new InterestWeights(
			new TopicInterests(Map.of("a", 0.8, "b", 1.2)), StorylineInterests.NEUTRAL_ALL);

	@Test
	void theInterestWeightCanPutALowerIntensityRowFirst() {
		// A 50 × 0.8 = 40, B 45 × 1.2 = 54
		BigRow a = row(1, "50", candidate(11, "a"));
		BigRow b = row(2, "45", candidate(21, "b"));

		assertThat(heads(BigRowOrder.order(List.of(a, b), WEIGHTS))).containsExactly(2L, 1L);
	}

	@Test
	void neutralWeightsKeepTheBigJudgementOrder() {
		// 대조 — 관심 신호가 없으면 보도 강도 순서 그대로
		BigRow a = row(1, "50", candidate(11, "a"));
		BigRow b = row(2, "45", candidate(21, "b"));

		assertThat(heads(BigRowOrder.order(List.of(a, b), InterestWeights.NEUTRAL_ALL))).containsExactly(1L, 2L);
	}

	@Test
	void equalScoresKeepTheGivenOrder() {
		BigRow first = row(1, "30", candidate(11, "n"));
		BigRow second = row(2, "30", candidate(21, "n"));

		assertThat(heads(BigRowOrder.order(List.of(second, first), WEIGHTS))).containsExactly(2L, 1L);
	}

	@Test
	void aRowWithoutCandidatesIsNeutral() {
		// 후보 없는 행 40 × 1 = 40 > 후보 있는 행 39 × 1 = 39
		BigRow empty = row(1, "40", new BigEvent(new EventId(11), BigDecimal.ONE, null));
		BigRow plain = row(2, "39", candidate(21, "n"));

		assertThat(heads(BigRowOrder.order(List.of(plain, empty), WEIGHTS))).containsExactly(1L, 2L);
	}

	@Test
	void eventsWithoutACandidateAreLeftOutOfTheAverage() {
		// 후보 1.2 하나 + 후보 없는 사건 하나 → 가중치 1.2, 45 × 1.2 = 54 > 50 × 1 = 50 (후보 없는 사건을 1 로 넣으면 49.5)
		BigRow mixed = row(1, "45", candidate(11, "b"), new BigEvent(new EventId(12), BigDecimal.ONE, null));
		BigRow plain = row(2, "50", candidate(21, "n"));

		assertThat(heads(BigRowOrder.order(List.of(plain, mixed), WEIGHTS))).containsExactly(1L, 2L);
	}

	private static BigRow row(long head, String intensity, BigEvent... events) {
		StorylineId id = new StorylineId(head);
		return new BigRow(id, id, new BigDecimal(intensity), Arrays.asList(events));
	}

	/** 주제 중분류 하나인 후보 사건을 가진 큰 판정 행의 사건 */
	private static BigEvent candidate(long id, String subtopic) {
		return new BigEvent(new EventId(id), BigDecimal.ONE, Facts.event(id, Facts.article(id).subtopics(subtopic)));
	}

	private static List<Long> heads(List<BigRow> rows) {
		return rows.stream().map(row -> row.head().value()).toList();
	}

}
