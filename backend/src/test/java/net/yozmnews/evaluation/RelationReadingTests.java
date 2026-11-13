package net.yozmnews.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 관계 판정 원본을 다시 읽은 값 — 답 다섯이 모두 있고 확률이 0 ~ 1 의 유한값인 것만 읽은 값이 된다. 읽지 못함의 문구는 비지 않는다.
 * <p>
 * 지키는 규칙: evaluation-R50
 */
class RelationReadingTests {

	private static final Map<RelationAnswer, Double> ANSWERS = Map.of(RelationAnswer.NONE, 0.1,
			RelationAnswer.SECOND_PART_OF_FIRST, 0.7, RelationAnswer.FIRST_PART_OF_SECOND, 0.05,
			RelationAnswer.FIRST_LED_TO_SECOND, 0.1, RelationAnswer.SECOND_LED_TO_FIRST, 0.05);

	@Test
	void keepsAllFiveAnswersAndBothOneMatterProbabilities() {
		RelationReading.Read read = new RelationReading.Read(ANSWERS, 0.8, 0.6);

		assertThat(read.probabilities()).containsOnlyKeys(RelationAnswer.values())
			.containsEntry(RelationAnswer.SECOND_PART_OF_FIRST, 0.7)
			.containsEntry(RelationAnswer.NONE, 0.1);
		assertThat(read.firstOneMatter()).isEqualTo(0.8);
		assertThat(read.secondOneMatter()).isEqualTo(0.6);
	}

	@Test
	void rejectsAReadingWithoutAllFiveAnswers() {
		Map<RelationAnswer, Double> four = new EnumMap<>(ANSWERS);
		four.remove(RelationAnswer.SECOND_LED_TO_FIRST);

		assertThatIllegalArgumentException().isThrownBy(() -> new RelationReading.Read(four, 0.8, 0.6))
			.withMessageStartingWith("답 다섯의 확률이 모두 있어야 한다");
	}

	@Test
	void rejectsAProbabilityOutsideZeroToOne() {
		Map<RelationAnswer, Double> above = new EnumMap<>(ANSWERS);
		above.put(RelationAnswer.NONE, 1.2);

		assertThatIllegalArgumentException().isThrownBy(() -> new RelationReading.Read(above, 0.8, 0.6))
			.withMessage("확률은 0 ~ 1 의 유한값이다: 1.2");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationReading.Read(ANSWERS, Double.NaN, 0.6))
			.withMessage("확률은 0 ~ 1 의 유한값이다: NaN");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationReading.Read(ANSWERS, 0.8, -0.1))
			.withMessage("확률은 0 ~ 1 의 유한값이다: -0.1");
	}

	@Test
	void rejectsABlankUnreadMessage() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new RelationReading.Unread(RelationReading.Unread.Reason.UNREADABLE, " "))
			.withMessage("문구가 비었다");
	}

}
