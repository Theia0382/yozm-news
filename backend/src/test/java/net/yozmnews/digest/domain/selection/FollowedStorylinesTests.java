package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import net.yozmnews.digest.domain.interest.InterestWeights;
import net.yozmnews.event.EventId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.TopicSubscription;
import org.junit.jupiter.api.Test;

/**
 * 주제 구독 밖 관심 흐름의 길 — 관심 흐름의 사건 가운데 구독 행과 겹치지 않는 사건을 흐름마다 하나, 순위대로. 구독 행은 정치(행정 · 입법)와
 * 시장이다(Facts.politicsAndMarkets).
 * <p>
 * 지키는 규칙: digest-R52
 */
class FollowedStorylinesTests {

	private static final StorylineId F1 = new StorylineId(100);

	private static final StorylineId F2 = new StorylineId(200);

	private static final StorylineId OTHER = new StorylineId(300);

	@Test
	void oneEventPerFollowedFlowOutsideTheSubscriptionsInRankOrder() {
		// F1 의 a(분쟁 4) · b(분쟁 3) — 구독 밖, F2 의 c(정치 5) — 구독 안이라 이 길이 아니다
		CandidateEvent a = Facts.withTops(Facts.single(Facts.article(1).subtopics("world.conflict").importance(4.0)), F1);
		CandidateEvent b = Facts.withTops(Facts.single(Facts.article(2).subtopics("world.conflict").importance(3.0)), F1);
		CandidateEvent c = Facts.withTops(Facts.single(Facts.article(3).importance(5.0)), F2);
		// 대조 — 관심 흐름이 아닌 꼭대기의 구독 밖 사건은 들지 않는다
		CandidateEvent d = Facts.withTops(Facts.single(Facts.article(4).subtopics("world.conflict").importance(9.0)),
				OTHER);

		assertThat(ids(pick(List.of(b, c, a, d), Facts.politicsAndMarkets(), Set.of(F1, F2), Set.of())))
			.containsExactly(1L);
	}

	@Test
	void anEventOnTwoFollowedFlowsServesBoth() {
		// e 는 F1 · F2 둘 다 — 순위 앞이라 두 흐름을 함께 채워 f(F2) 는 들지 않는다
		CandidateEvent e = Facts.withTops(Facts.single(Facts.article(5).subtopics("world.conflict").importance(4.0)), F1,
				F2);
		CandidateEvent f = Facts.withTops(Facts.single(Facts.article(6).subtopics("world.conflict").importance(3.0)), F2);

		assertThat(ids(pick(List.of(f, e), Facts.politicsAndMarkets(), Set.of(F1, F2), Set.of()))).containsExactly(5L);
	}

	@Test
	void excludedTakenAndUnfollowedEventsStayOut() {
		CandidateEvent taken = Facts.withTops(Facts.single(Facts.article(7).subtopics("world.conflict")), F1);
		CandidateEvent region = Facts.withTops(Facts.single(Facts.article(8).subtopics("world.conflict").regions("us")),
				F2);
		UserFilter excludingUs = UserFilter.of(Facts.interests(
				List.of(new TopicSubscription("politics", Set.of("politics.executive"))), Set.of("us"), Set.of(), Set.of()),
				Set.of(), Set.of());

		// 대조 — 큰 슬롯이 쓰지 않았으면 든다
		assertThat(ids(pick(List.of(taken), Facts.politicsAndMarkets(), Set.of(F1), Set.of()))).containsExactly(7L);
		// 큰 슬롯이 쓴 사건 · 사건 제외(지역)에 걸린 사건 · 관심 흐름이 없으면 빈 결과
		assertThat(pick(List.of(taken, region), excludingUs, Set.of(F1, F2), Set.of(new EventId(7)))).isEmpty();
		assertThat(pick(List.of(taken), Facts.politicsAndMarkets(), Set.of(), Set.of())).isEmpty();
		// 사건 제외(꼭대기가 모두 거른 꼭대기)에 걸린 사건도 들지 않는다
		UserFilter excludingF1 = UserFilter.of(Facts.interests(
				List.of(new TopicSubscription("politics", Set.of("politics.executive"))), Set.of(), Set.of(), Set.of()),
				Set.of(), Set.of(F1));
		assertThat(pick(List.of(taken), excludingF1, Set.of(F1), Set.of())).isEmpty();
	}

	private static List<CandidateEvent> pick(List<CandidateEvent> pool, UserFilter filter, Set<StorylineId> followed,
			Set<EventId> taken) {
		return FollowedStorylines.pick(pool, filter, InterestWeights.NEUTRAL_ALL, followed, taken);
	}

	private static List<Long> ids(List<CandidateEvent> events) {
		return events.stream().map(event -> event.facts().id().value()).toList();
	}

}
