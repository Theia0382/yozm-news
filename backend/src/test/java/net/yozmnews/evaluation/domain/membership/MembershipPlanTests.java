package net.yozmnews.evaluation.domain.membership;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EventDescriptor;
import net.yozmnews.evaluation.MembershipQuery;
import net.yozmnews.evaluation.domain.evaluator.CandidateEvent;
import net.yozmnews.evaluation.domain.evaluator.MembershipInput;
import net.yozmnews.evaluation.domain.evaluator.MembershipWording;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * 물을 후보 고르기 — 지워진 서술 기사 빼기, 서술이 남지 않은 후보 빼기, 요청 순서, 요약 정리.
 * <p>
 * 지키는 규칙: evaluation-R38
 */
class MembershipPlanTests {

	private static final MembershipWording WORDING = new MembershipWording("w-test", "같은 사건인가");

	private static final ArticleContent JUDGED = content(1, "서울 시장 선거 결과", "개표가 끝났다");

	@Test
	void asksEveryCandidateInTheRequestedOrderWithTheTitlesOfItsDescriptions() {
		MembershipQuery query = new MembershipQuery(JUDGED.id(),
				List.of(descriptor(15, 7), new EventDescriptor(12, List.of(new ArticleId(3), new ArticleId(4)))));
		Map<ArticleId, ArticleContent> found = found(content(3, "서울 시장 선거 개표", null),
				content(4, "서울 시장 당선 확정", null), content(7, "부산 항만 파업", null));

		MembershipPlan.Plan.Ask ask = (MembershipPlan.Plan.Ask) MembershipPlan.of(query, JUDGED, found, WORDING);

		assertThat(ask.input()).isEqualTo(new MembershipInput("서울 시장 선거 결과", "개표가 끝났다",
				List.of(new CandidateEvent(15, List.of("부산 항만 파업")),
						new CandidateEvent(12, List.of("서울 시장 선거 개표", "서울 시장 당선 확정"))),
				WORDING));
		assertThat(ask.asked()).containsExactly(Map.entry(15L, List.of(new ArticleId(7))),
				Map.entry(12L, List.of(new ArticleId(3), new ArticleId(4))));
	}

	@Test
	void leavesOutRemovedDescriptionsAndCandidatesWithoutAnyLeft() {
		MembershipQuery query = new MembershipQuery(JUDGED.id(),
				List.of(new EventDescriptor(12, List.of(new ArticleId(3), new ArticleId(99))), descriptor(15, 98),
						descriptor(16, 5)));
		Map<ArticleId, ArticleContent> found = found(content(3, "서울 시장 선거 개표", null), content(5, "  ", null));

		MembershipPlan.Plan.Ask ask = (MembershipPlan.Plan.Ask) MembershipPlan.of(query, JUDGED, found, WORDING);

		assertThat(ask.input().candidates()).containsExactly(new CandidateEvent(12, List.of("서울 시장 선거 개표")));
		assertThat(ask.asked()).containsExactly(Map.entry(12L, List.of(new ArticleId(3))));
	}

	@Test
	void recordsOnlyTheDescriptionsWhoseTitlesWereAsked() {
		MembershipQuery query = new MembershipQuery(JUDGED.id(),
				List.of(new EventDescriptor(12, List.of(new ArticleId(3), new ArticleId(5), new ArticleId(6)))));
		Map<ArticleId, ArticleContent> found = found(content(3, "서울 시장 선거 개표", null), content(5, "  ", null),
				content(6, " ".repeat(200) + "서울", null));

		MembershipPlan.Plan.Ask ask = (MembershipPlan.Plan.Ask) MembershipPlan.of(query, JUDGED, found, WORDING);

		assertThat(ask.input().candidates()).containsExactly(new CandidateEvent(12, List.of("서울 시장 선거 개표")));
		assertThat(ask.asked()).containsExactly(Map.entry(12L, List.of(new ArticleId(3))));
	}

	@Test
	void asksNothingWhenNoCandidateHasADescriptionLeft() {
		MembershipQuery query = new MembershipQuery(JUDGED.id(), List.of(descriptor(12, 98), descriptor(15, 99)));

		assertThat(MembershipPlan.of(query, JUDGED, Map.of(), WORDING)).isEqualTo(new MembershipPlan.Plan.NothingToAsk());
	}

	@Test
	void treatsABlankSummaryAsNoSummary() {
		MembershipQuery query = new MembershipQuery(new ArticleId(2), List.of(descriptor(12, 3)));

		MembershipPlan.Plan.Ask ask = (MembershipPlan.Plan.Ask) MembershipPlan.of(query, content(2, "제목", " "),
				found(content(3, "서울 시장 선거 개표", null)), WORDING);

		assertThat(ask.input().summary()).isNull();
	}

	private static EventDescriptor descriptor(long eventId, long article) {
		return new EventDescriptor(eventId, List.of(new ArticleId(article)));
	}

	private static ArticleContent content(long id, String title, @Nullable String summary) {
		return new ArticleContent(new ArticleId(id), title, summary, URI.create("https://plan.example/" + id),
				Instant.parse("2026-10-28T00:00:00Z"), List.of());
	}

	private static Map<ArticleId, ArticleContent> found(ArticleContent... contents) {
		Map<ArticleId, ArticleContent> found = new HashMap<>();
		for (ArticleContent content : contents) {
			found.put(content.id(), content);
		}
		return found;
	}

}
