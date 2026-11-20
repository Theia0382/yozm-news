package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import net.yozmnews.event.EventId;
import org.junit.jupiter.api.Test;

/**
 * 사건 제외 — 사건의 지역이 제외와 겹치지 않고 그 사용자의 값이 싫어요인 사건이 아니다. 나중에 정해지는 사건의 싫어요는 더해 넣는다.
 * <p>
 * 지키는 규칙: digest-R17
 */
class UserFilterTests {

	private static final EventFacts DOMESTIC = Facts.eventFacts(10, List.of(Facts.article(1)));

	private static final EventFacts IN_US = Facts.eventFacts(11, List.of(Facts.article(2).regions("us")));

	@Test
	void aDislikedEventDoesNotPass() {
		UserFilter filter = UserFilter.of(Facts.interests(List.of(), Set.of(), Set.of(), Set.of()),
				Set.of(new EventId(10)));

		assertThat(filter.passesEvent(DOMESTIC)).isFalse();
	}

	@Test
	void anEventNeitherDislikedNorInAnExcludedRegionPasses() {
		// 대조 — 다른 사건의 싫어요 · 겹치지 않는 지역 제외는 빼지 않는다
		UserFilter filter = UserFilter.of(Facts.interests(List.of(), Set.of("jp"), Set.of(), Set.of()),
				Set.of(new EventId(99)));

		assertThat(filter.passesEvent(DOMESTIC)).isTrue();
		assertThat(filter.passesEvent(IN_US)).isTrue();
	}

	@Test
	void anEventInAnExcludedRegionDoesNotPass() {
		UserFilter filter = UserFilter.of(Facts.interests(List.of(), Set.of("us"), Set.of(), Set.of()), Set.of());

		assertThat(filter.passesEvent(IN_US)).isFalse();
		assertThat(filter.passesEvent(DOMESTIC)).isTrue();
	}

	@Test
	void dislikesAddedLaterJoinTheEarlierOnes() {
		UserFilter filter = UserFilter.of(Facts.interests(List.of(), Set.of(), Set.of(), Set.of()),
				Set.of(new EventId(10)))
			.withDisliked(Set.of(new EventId(11)));

		assertThat(filter.passesEvent(DOMESTIC)).isFalse();
		assertThat(filter.passesEvent(IN_US)).isFalse();
		assertThat(filter.dislikedEvents()).containsExactlyInAnyOrder(new EventId(10), new EventId(11));
	}

}
