package net.yozmnews.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 관계 판정 원본을 다시 읽은 값 — 한 문구 판의 답이 모두 있고 확률이 0 ~ 1 의 유한값인 것만 읽은 값이 된다. 읽지 못함의 문구는 비지 않는다.
 * <p>
 * 지키는 규칙: evaluation-R50
 */
class RelationReadingTests {

	private static final Map<RelationAnswer, Double> ANSWERS = Map.of(RelationAnswer.NONE, 0.1,
			RelationAnswer.SECOND_PART_OF_FIRST, 0.7, RelationAnswer.FIRST_PART_OF_SECOND, 0.05,
			RelationAnswer.FIRST_LED_TO_SECOND, 0.1, RelationAnswer.SECOND_LED_TO_FIRST, 0.05);

	@Test
	void keepsTheAnswersOfTheEarlierWordingAndBothOneMatterProbabilities() {
		RelationReading.Read read = new RelationReading.Read(ANSWERS, 0.8, 0.6);

		assertThat(read.probabilities())
			.containsOnlyKeys(RelationAnswer.NONE, RelationAnswer.SECOND_PART_OF_FIRST, RelationAnswer.FIRST_PART_OF_SECOND,
					RelationAnswer.FIRST_LED_TO_SECOND, RelationAnswer.SECOND_LED_TO_FIRST)
			.containsEntry(RelationAnswer.SECOND_PART_OF_FIRST, 0.7)
			.containsEntry(RelationAnswer.NONE, 0.1);
		assertThat(read.firstOneMatter()).isEqualTo(0.8);
		assertThat(read.secondOneMatter()).isEqualTo(0.6);
	}

	@Test
	void keepsTheSevenAnswersOfTheWordingWithTheFragmentAndParallelAnswers() {
		Map<RelationAnswer, Double> seven = new EnumMap<>(ANSWERS);
		seven.put(RelationAnswer.SAME_MATTER, 0.3);
		seven.put(RelationAnswer.PARALLEL, 0.2);

		assertThat(new RelationReading.Read(seven, 0.8, 0.6).probabilities()).containsOnlyKeys(RelationAnswer.values())
			.containsEntry(RelationAnswer.SAME_MATTER, 0.3)
			.containsEntry(RelationAnswer.PARALLEL, 0.2);
	}

	@Test
	void rejectsAReadingWhoseAnswersAreNotThoseOfOneWording() {
		Map<RelationAnswer, Double> four = new EnumMap<>(ANSWERS);
		four.remove(RelationAnswer.SECOND_LED_TO_FIRST);
		// 조각 답은 있는데 해당 없음이 없다 — 어느 판의 답도 아니다
		Map<RelationAnswer, Double> withoutNone = new EnumMap<>(ANSWERS);
		withoutNone.remove(RelationAnswer.NONE);
		withoutNone.put(RelationAnswer.SAME_MATTER, 0.3);

		assertThatIllegalArgumentException().isThrownBy(() -> new RelationReading.Read(four, 0.8, 0.6))
			.withMessageStartingWith("한 문구 판의 답이 모두 있어야 한다");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationReading.Read(withoutNone, 0.8, 0.6))
			.withMessageStartingWith("한 문구 판의 답이 모두 있어야 한다");
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
