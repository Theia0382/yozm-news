package net.yozmnews.evaluation.domain.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.article.ArticleId;
import org.junit.jupiter.api.Test;

/**
 * 저장할 판정 원본의 물은 후보 하나 — 후보 ID · 서술 기사 · 확률을 확인하는 정본({@link RecordedCandidate#checked})과 그것을 부르는 생성자.
 * <p>
 * 지키는 규칙: evaluation-R38 · evaluation-R52
 */
class RecordedCandidateTests {

	@Test
	void refusesACandidateIdBelowOneNoDescriptionsOrAProbabilityOutsideZeroToOne() {
		assertThatIllegalArgumentException().isThrownBy(() -> new RecordedCandidate(0, List.of(new ArticleId(3)), 0.83))
			.withMessage("후보 ID 는 1 이상이다: 0");
		assertThatIllegalArgumentException().isThrownBy(() -> new RecordedCandidate(12, List.of(), 0.83))
			.withMessage("서술 기사가 없다");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new RecordedCandidate(12, List.of(new ArticleId(3)), 1.01))
			.withMessage("확률은 0 ~ 1 의 유한값이다 — RecordedCandidate.probability: 1.01");
	}

	@Test
	void acceptsTheEndsOfTheProbabilityRangeAndCopiesTheDescriptions() {
		List<ArticleId> described = new ArrayList<>(List.of(new ArticleId(3), new ArticleId(4)));
		RecordedCandidate zero = new RecordedCandidate(12, described, 0.0);
		described.clear();

		assertThat(zero.describedArticles()).containsExactly(new ArticleId(3), new ArticleId(4));
		assertThat(new RecordedCandidate(12, List.of(new ArticleId(3)), 1.0).probability()).isEqualTo(1.0);
	}

	@Test
	void theCheckNamesTheIdAndTheProbabilityAsTheCallerGives() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> RecordedCandidate.checked(0, List.of(new ArticleId(3)), 0.5, "사건", "x.p"))
			.withMessage("사건 ID 는 1 이상이다: 0");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> RecordedCandidate.checked(12, List.of(new ArticleId(3)), Double.NaN, "사건", "x.p"))
			.withMessage("확률은 0 ~ 1 의 유한값이다 — x.p: NaN");
	}

}
