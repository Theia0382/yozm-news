package net.yozmnews.evaluation.domain.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.evaluation.RelationAnswer;
import org.junit.jupiter.api.Test;

/**
 * 관계 판정의 문구 · state 조립과 서술 사건의 길이 상한.
 * <p>
 * 지키는 규칙: evaluation-R48
 */
class RelationWordingTests {

	@Test
	void writesBothFlowsWithUtcDaysAndTheSummariesUnderTheirTitles() {
		FlowText first = new FlowText(6, Instant.parse("2026-10-29T15:10:00Z"), Instant.parse("2026-11-02T01:00:00Z"),
				List.of(new HappeningText(Instant.parse("2026-10-29T15:10:00Z"), "특검법 본회의 통과", "재석 과반 찬성"),
						// 한국 시간으로는 11-01 이다 — 날짜는 UTC 로 적는다
						new HappeningText(Instant.parse("2026-10-31T23:30:00Z"), "야당 거부권 건의", null)));
		FlowText second = new FlowText(3, Instant.parse("2026-11-01T02:00:00Z"), Instant.parse("2026-11-03T09:00:00Z"),
				List.of(new HappeningText(Instant.parse("2026-11-01T02:00:00Z"), "특검 후보 추천", null)));

		assertThat(RelationWording.CURRENT.state(first, second)).isEqualTo("""
				Flow A (6 happenings, 10-29 to 11-02):
				- [10-29] 특검법 본회의 통과
				  summary: 재석 과반 찬성
				- [10-31] 야당 거부권 건의

				Flow B (3 happenings, 11-01 to 11-03):
				- [11-01] 특검 후보 추천""");
	}

	@Test
	void theCurrentWordingIsTheOneThatWasMeasured() {
		RelationWording wording = RelationWording.CURRENT;

		assertThat(wording.version()).isEqualTo("relation-1");
		assertThat(wording.question()).isEqualTo("What is the relation between flow A and flow B?");
		assertThat(wording.oneMatterQuestion("A")).isEqualTo("Is flow A the unfolding of one matter, rather than a "
				+ "collection of separate happenings that are only of a similar kind?");
		assertThat(wording.oneMatterQuestion("B")).isEqualTo("Is flow B the unfolding of one matter, rather than a "
				+ "collection of separate happenings that are only of a similar kind?");
		assertThat(wording.options()).containsOnlyKeys(RelationAnswer.values());
		assertThat(wording.options().get(RelationAnswer.SECOND_PART_OF_FIRST)).isEqualTo("B is narrower than A and lies "
				+ "inside it: B covers one reaction to A, one stage of A, or one strand of A, and is still about the "
				+ "matter of A.");
		assertThat(wording.options().get(RelationAnswer.FIRST_LED_TO_SECOND)).isEqualTo("B is about a different matter "
				+ "from A. B has its own actors and its own course and would go on even if A ended, but it arose "
				+ "because of what happened in A.");
	}

	@Test
	void rejectsAWordingWithoutAllFiveOptionsOrThePlaceOfTheFlowName() {
		Map<RelationAnswer, String> all = new EnumMap<>(RelationAnswer.class);
		for (RelationAnswer answer : RelationAnswer.values()) {
			all.put(answer, "선택지 " + answer);
		}
		Map<RelationAnswer, String> four = new EnumMap<>(all);
		four.remove(RelationAnswer.NONE);

		assertThat(new RelationWording("w", "관계는", all, "{flow} 는 한 사안인가").oneMatterQuestion("A"))
			.isEqualTo("A 는 한 사안인가");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationWording("w", "관계는", four, "{flow} 는"))
			.withMessageStartingWith("선택지는 답 다섯 모두다");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationWording("w", "관계는", all, "한 사안인가"))
			.withMessage("한 사안 질문의 틀에 흐름 이름의 자리가 없다");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationWording(" ", "관계는", all, "{flow} 는"))
			.withMessage("문구 판 · 질문이 비었다");
	}

	@Test
	void cutsTheTitleAtTwoHundredAndTheSummaryAtOneHundredSixtyCodePoints() {
		Instant at = Instant.parse("2026-11-01T00:00:00Z");
		// 😀 는 코드 포인트 하나 · char 둘이다 — 쌍을 가르지 않는다
		HappeningText cut = HappeningText.limited(at, "가".repeat(199) + "😀나", "다".repeat(159) + "😀라").orElseThrow();

		assertThat(cut.title()).isEqualTo("가".repeat(199) + "😀");
		assertThat(cut.summary()).isEqualTo("다".repeat(159) + "😀");
		assertThat(HappeningText.MAX_SUMMARY_CODE_POINTS).isEqualTo(160);
		assertThat(CandidateEvent.MAX_TITLE_CODE_POINTS).isEqualTo(200);
	}

	@Test
	void dropsABlankTitleAndTreatsABlankSummaryAsNone() {
		Instant at = Instant.parse("2026-11-01T00:00:00Z");

		assertThat(HappeningText.limited(at, "   ", "요약")).isEmpty();
		assertThat(HappeningText.limited(at, "제목  ", "  ")).isEqualTo(Optional.of(new HappeningText(at, "제목", null)));
		assertThat(HappeningText.limited(at, "제목", null)).isEqualTo(Optional.of(new HappeningText(at, "제목", null)));
		assertThatIllegalArgumentException().isThrownBy(() -> new HappeningText(at, "가".repeat(201), null))
			.withMessage("서술 제목은 200 코드 포인트까지다");
		assertThatIllegalArgumentException().isThrownBy(() -> new HappeningText(at, "제목", "다".repeat(161)))
			.withMessage("요약문은 160 코드 포인트까지다");
	}

}
