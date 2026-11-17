package net.yozmnews.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import org.junit.jupiter.api.Test;

/**
 * 흐름 소속 판정의 공개 값 — 요청 · 후보 흐름의 개수 상한과 값 규칙(사건 소속과 같은 상한), 판정 결과의 값 규칙, 문구의 이름.
 * <p>
 * 지키는 규칙: evaluation-R51 · evaluation-R52
 */
class FlowMembershipQueryTests {

	private static final ArticleId JUDGED = new ArticleId(1);

	@Test
	void acceptsTenCandidatesAndRefusesEleven() {
		assertThat(new FlowMembershipQuery(JUDGED, candidates(10)).candidates()).hasSize(10);
		assertThatIllegalArgumentException().isThrownBy(() -> new FlowMembershipQuery(JUDGED, candidates(11)))
			.withMessage("후보 흐름은 10개까지다: 11");
	}

	@Test
	void refusesNoCandidatesARepeatedFlowOrTheJudgedArticleAmongTheDescriptions() {
		assertThatIllegalArgumentException().isThrownBy(() -> new FlowMembershipQuery(JUDGED, List.of()))
			.withMessage("후보 흐름이 없다");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new FlowMembershipQuery(JUDGED, List.of(candidate(305, 3), candidate(305, 4))))
			.withMessage("후보 흐름 ID 가 겹친다: 305");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new FlowMembershipQuery(JUDGED,
					List.of(candidate(305, 3), new FlowCandidate(306, List.of(new ArticleId(4), JUDGED)))))
			.withMessage("판정할 기사가 후보 흐름 306 의 서술 기사다");
	}

	@Test
	void keepsTheCandidatesInTheGivenOrder() {
		assertThat(new FlowMembershipQuery(JUDGED, List.of(candidate(306, 7), candidate(305, 3))).candidates())
			.extracting(FlowCandidate::flowId)
			.containsExactly(306L, 305L);
	}

	@Test
	void acceptsFiveDescriptionsAndRefusesSix() {
		assertThat(new FlowCandidate(305, articles(5)).articleIds()).hasSize(5);
		assertThatIllegalArgumentException().isThrownBy(() -> new FlowCandidate(305, articles(6)))
			.withMessage("서술 기사는 5개까지다: 6");
	}

	@Test
	void refusesAFlowIdBelowOneNoDescriptionsOrARepeatedDescription() {
		assertThatIllegalArgumentException().isThrownBy(() -> new FlowCandidate(0, List.of(new ArticleId(3))))
			.withMessage("흐름 ID 는 1 이상이다: 0");
		assertThatIllegalArgumentException().isThrownBy(() -> new FlowCandidate(305, List.of()))
			.withMessage("서술 기사가 없다");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new FlowCandidate(305, List.of(new ArticleId(3), new ArticleId(3))))
			.withMessage("서술 기사가 겹친다");
	}

	@Test
	void aJudgementHoldsProbabilitiesOfFlowsOnly() {
		assertThat(new FlowMembershipJudgement.Judged(null, Map.of()).probabilities()).isEmpty();
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new FlowMembershipJudgement.Judged(null, Map.of(305L, 0.5)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new FlowMembershipJudgement.Judged(new FlowMembershipJudgementId(1), Map.of()));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new FlowMembershipJudgement.Judged(new FlowMembershipJudgementId(1), Map.of(0L, 0.5)))
			.withMessage("흐름 ID 는 1 이상이다: 0");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new FlowMembershipJudgement.Judged(new FlowMembershipJudgementId(1), Map.of(305L, 1.5)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new FlowMembershipJudgement.Failed(EvaluatorFailureKind.TRANSIENT, " "));
		assertThatIllegalArgumentException().isThrownBy(() -> new FlowMembershipJudgementId(0));
	}

	/** 흐름 305 부터 하나씩 늘린 후보 — 서술 기사는 흐름마다 다르다 */
	private static List<FlowCandidate> candidates(int count) {
		List<FlowCandidate> candidates = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			candidates.add(candidate(305 + i, 100 + i));
		}
		return candidates;
	}

	private static FlowCandidate candidate(long flowId, long article) {
		return new FlowCandidate(flowId, List.of(new ArticleId(article)));
	}

	private static List<ArticleId> articles(int count) {
		List<ArticleId> articles = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			articles.add(new ArticleId(100 + i));
		}
		return articles;
	}

}
