package net.yozmnews.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import org.junit.jupiter.api.Test;

/**
 * 관계 판정의 요청 · 결과 값 — 제약을 어긴 값은 만들 수 없다.
 * <p>
 * 지키는 규칙: evaluation-R48 · evaluation-R49
 */
class RelationQueryTests {

	private static final Instant START = Instant.parse("2026-11-01T00:00:00Z");

	private static final Instant END = Instant.parse("2026-11-05T00:00:00Z");

	@Test
	void takesTenDescribedHappeningsAndThreeSummaries() {
		FlowDescriptor flow = new FlowDescriptor(7, 40, START, END, described(10, 3));

		assertThat(flow.described()).hasSize(10);
		assertThat(FlowDescriptor.MAX_DESCRIBED).isEqualTo(10);
		assertThat(FlowDescriptor.MAX_SUMMARIZED).isEqualTo(3);
	}

	@Test
	void rejectsAFlowBeyondItsLimits() {
		assertThatIllegalArgumentException().isThrownBy(() -> new FlowDescriptor(7, 40, START, END, described(11, 0)))
			.withMessage("서술 사건은 10개까지다: 11");
		assertThatIllegalArgumentException().isThrownBy(() -> new FlowDescriptor(7, 40, START, END, described(5, 4)))
			.withMessage("요약문을 싣는 서술 사건은 3개까지다: 4");
		assertThatIllegalArgumentException().isThrownBy(() -> new FlowDescriptor(7, 40, START, END, List.of()))
			.withMessage("서술 사건이 없다");
		assertThatIllegalArgumentException().isThrownBy(() -> new FlowDescriptor(0, 40, START, END, described(1, 0)))
			.withMessage("흐름 ID 는 1 이상이다: 0");
		assertThatIllegalArgumentException().isThrownBy(() -> new FlowDescriptor(7, 0, START, END, described(1, 0)))
			.withMessage("흐름의 사건 수는 1 이상이다: 0");
		assertThatIllegalArgumentException().isThrownBy(() -> new FlowDescriptor(7, 40, END, START, described(1, 0)))
			.withMessageStartingWith("첫 기사 시각이 마지막 기사 시각보다 늦다");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new FlowDescriptor(7, 40, START, END,
					List.of(new FlowHappening(new ArticleId(3), START, false),
							new FlowHappening(new ArticleId(3), END, false))))
			.withMessage("서술 기사가 겹친다: 3");
	}

	@Test
	void aFlowOfOneInstantIsValid() {
		assertThat(new FlowDescriptor(7, 1, START, START, described(1, 1)).firstAt()).isEqualTo(START);
	}

	@Test
	void rejectsTheSameFlowTwiceButTakesASharedArticle() {
		FlowDescriptor seven = new FlowDescriptor(7, 3, START, END, described(2, 0));

		assertThatIllegalArgumentException().isThrownBy(() -> new RelationQuery(seven, seven))
			.withMessage("두 흐름 ID 가 같다: 7");
		// 두 흐름이 함께 가진 사건의 대표 기사는 양쪽에 있어도 된다
		assertThat(new RelationQuery(seven, new FlowDescriptor(8, 3, START, END, described(2, 0))).second().flowId())
			.isEqualTo(8);
	}

	@Test
	void aJudgementCarriesAllFiveAnswers() {
		Map<RelationAnswer, Double> all = new EnumMap<>(RelationAnswer.class);
		for (RelationAnswer answer : RelationAnswer.values()) {
			all.put(answer, 0.2);
		}
		Map<RelationAnswer, Double> four = new EnumMap<>(all);
		four.remove(RelationAnswer.NONE);
		RelationJudgementId id = new RelationJudgementId(1);

		assertThat(new RelationJudgement.Judged(id, all, 0.0, 1.0).probabilities()).hasSize(5);
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationJudgement.Judged(id, four, 0.5, 0.5))
			.withMessageStartingWith("답 다섯의 확률이 모두 있어야 한다");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationJudgement.Judged(id, all, 1.1, 0.5))
			.withMessage("확률은 0 ~ 1 의 유한값이다: 1.1");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationJudgement.Judged(id, all, 0.5, Double.NaN))
			.withMessage("확률은 0 ~ 1 의 유한값이다: NaN");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationJudgementId(0))
			.withMessage("판정 원본 ID 는 1 이상이다: 0");
	}

	@Test
	void noneComesFirstAmongTheAnswers() {
		// 부르는 쪽이 확률이 같을 때 선언 순서의 앞의 답을 고른다 — 해당 없음이 맨 앞이어야 같으면 맺지 않는다
		assertThat(RelationAnswer.values()).containsExactly(RelationAnswer.NONE, RelationAnswer.SECOND_PART_OF_FIRST,
				RelationAnswer.FIRST_PART_OF_SECOND, RelationAnswer.FIRST_LED_TO_SECOND,
				RelationAnswer.SECOND_LED_TO_FIRST);
	}

	private static List<FlowHappening> described(int count, int summarized) {
		List<FlowHappening> described = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			described.add(new FlowHappening(new ArticleId(100 + i), START.plusSeconds(i), i < summarized));
		}
		return described;
	}

}
