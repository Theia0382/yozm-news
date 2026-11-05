package net.yozmnews.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import org.junit.jupiter.api.Test;

/**
 * 사건 소속 판정의 공개 값 — 요청 · 후보 사건의 개수 상한과 값 규칙, 판정 결과의 값 규칙.
 * <p>
 * 지키는 규칙: evaluation-R37 · evaluation-R38
 */
class MembershipQueryTests {

	private static final ArticleId JUDGED = new ArticleId(1);

	@Test
	void acceptsTenCandidatesAndRefusesEleven() {
		assertThat(new MembershipQuery(JUDGED, candidates(10)).candidates()).hasSize(10);
		assertThatIllegalArgumentException().isThrownBy(() -> new MembershipQuery(JUDGED, candidates(11)));
	}

	@Test
	void refusesNoCandidatesARepeatedEventOrTheJudgedArticleAmongTheDescriptions() {
		assertThatIllegalArgumentException().isThrownBy(() -> new MembershipQuery(JUDGED, List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> new MembershipQuery(JUDGED,
				List.of(descriptor(12, 3), descriptor(12, 4))));
		assertThatIllegalArgumentException().isThrownBy(() -> new MembershipQuery(JUDGED,
				List.of(descriptor(12, 3), new EventDescriptor(15, List.of(new ArticleId(4), JUDGED)))));
	}

	@Test
	void keepsTheCandidatesInTheGivenOrder() {
		assertThat(new MembershipQuery(JUDGED, List.of(descriptor(15, 7), descriptor(12, 3))).candidates())
			.extracting(EventDescriptor::eventId)
			.containsExactly(15L, 12L);
	}

	@Test
	void acceptsFiveDescriptionsAndRefusesSix() {
		assertThat(new EventDescriptor(12, articles(5)).articleIds()).hasSize(5);
		assertThatIllegalArgumentException().isThrownBy(() -> new EventDescriptor(12, articles(6)));
	}

	@Test
	void refusesAnEventIdBelowOneNoDescriptionsOrARepeatedDescription() {
		assertThatIllegalArgumentException().isThrownBy(() -> new EventDescriptor(0, List.of(new ArticleId(3))));
		assertThatIllegalArgumentException().isThrownBy(() -> new EventDescriptor(12, List.of()));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new EventDescriptor(12, List.of(new ArticleId(3), new ArticleId(3))));
	}

	@Test
	void aJudgementHoldsProbabilitiesOfEventsOnly() {
		assertThat(new MembershipJudgement.Judged(Map.of()).probabilities()).isEmpty();
		assertThatIllegalArgumentException().isThrownBy(() -> new MembershipJudgement.Judged(Map.of(0L, 0.5)));
		assertThatIllegalArgumentException().isThrownBy(() -> new MembershipJudgement.Judged(Map.of(12L, 1.5)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MembershipJudgement.Failed(EvaluatorFailureKind.TRANSIENT, " "));
	}

	/** 사건 12 부터 하나씩 늘린 후보 — 서술 기사는 사건마다 다르다 */
	private static List<EventDescriptor> candidates(int count) {
		List<EventDescriptor> candidates = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			candidates.add(descriptor(12 + i, 100 + i));
		}
		return candidates;
	}

	private static EventDescriptor descriptor(long eventId, long article) {
		return new EventDescriptor(eventId, List.of(new ArticleId(article)));
	}

	private static List<ArticleId> articles(int count) {
		List<ArticleId> articles = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			articles.add(new ArticleId(100 + i));
		}
		return articles;
	}

}
