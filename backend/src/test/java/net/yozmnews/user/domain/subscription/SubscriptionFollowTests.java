package net.yozmnews.user.domain.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 추적 씨앗 따라가기 판정 — 합쳐진 행은 남는 스토리라인으로 옮기고, 사라진 행은 빼며, 같은 남는 스토리라인으로 가는 행은 하나로 두고 추적 시각은
 * 가장 이른 것이다.
 * <p>
 * 지키는 규칙: user-R53
 */
class SubscriptionFollowTests {

	private static final Instant EARLY = Instant.parse("2026-11-01T00:00:00Z");

	private static final Instant LATE = Instant.parse("2026-11-03T00:00:00Z");

	@Test
	void aMergedRowMovesToItsSurvivorKeepingItsTime() {
		SubscriptionFollow follow = SubscriptionFollow.decide(Map.of(10L, EARLY), Map.of(10L, 20L));

		assertThat(follow.next()).containsExactly(Map.entry(20L, EARLY));
		assertThat(follow.moved()).containsExactly(new SubscriptionFollow.Move(10, 20, true));
		assertThat(follow.removed()).isEmpty();
		assertThat(follow.changed()).isTrue();
	}

	@Test
	void twoRowsMergedIntoOneSurvivorBecomeOneRowWithTheEarlierTime() {
		SubscriptionFollow follow = SubscriptionFollow.decide(Map.of(10L, LATE, 11L, EARLY), Map.of(10L, 30L, 11L, 30L));

		assertThat(follow.next()).containsExactly(Map.entry(30L, EARLY));
		// ID 순으로 본다 — 10 이 먼저 30 을 더하고, 11 은 이미 있는 30 으로 옮긴다
		assertThat(follow.moved()).containsExactly(new SubscriptionFollow.Move(10, 30, true),
				new SubscriptionFollow.Move(11, 30, false));
	}

	@Test
	void aRowMergedIntoAnAlreadySubscribedSurvivorAddsNothing() {
		SubscriptionFollow follow = SubscriptionFollow.decide(Map.of(10L, EARLY, 20L, LATE), Map.of(10L, 20L, 20L, 20L));

		assertThat(follow.next()).containsExactly(Map.entry(20L, EARLY));
		assertThat(follow.moved()).containsExactly(new SubscriptionFollow.Move(10, 20, false));
	}

	@Test
	void aRowWithoutAStorylineIsRemoved() {
		SubscriptionFollow follow = SubscriptionFollow.decide(Map.of(10L, EARLY, 20L, LATE), Map.of(20L, 20L));

		assertThat(follow.next()).containsExactly(Map.entry(20L, LATE));
		assertThat(follow.moved()).isEmpty();
		assertThat(follow.removed()).containsExactly(10L);
		assertThat(follow.changed()).isTrue();
	}

	@Test
	void rowsPointingAtThemselvesChangeNothing() {
		SubscriptionFollow follow = SubscriptionFollow.decide(Map.of(10L, EARLY, 20L, LATE), Map.of(10L, 10L, 20L, 20L));

		assertThat(follow.next()).containsOnly(Map.entry(10L, EARLY), Map.entry(20L, LATE));
		assertThat(follow.moved()).isEmpty();
		assertThat(follow.removed()).isEmpty();
		assertThat(follow.changed()).isFalse();
	}

}
