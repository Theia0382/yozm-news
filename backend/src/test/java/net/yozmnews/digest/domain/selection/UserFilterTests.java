package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import net.yozmnews.event.EventId;
import net.yozmnews.storyline.StorylineId;
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
				Set.of(new EventId(10)), Set.of());

		assertThat(filter.passesEvent(DOMESTIC, List.of())).isFalse();
	}

	@Test
	void anEventNeitherDislikedNorInAnExcludedRegionPasses() {
		// 대조 — 다른 사건의 싫어요 · 겹치지 않는 지역 제외는 빼지 않는다
		UserFilter filter = UserFilter.of(Facts.interests(List.of(), Set.of("jp"), Set.of(), Set.of()),
				Set.of(new EventId(99)), Set.of());

		assertThat(filter.passesEvent(DOMESTIC, List.of())).isTrue();
		assertThat(filter.passesEvent(IN_US, List.of())).isTrue();
	}

	@Test
	void anEventInAnExcludedRegionDoesNotPass() {
		UserFilter filter = UserFilter.of(Facts.interests(List.of(), Set.of("us"), Set.of(), Set.of()), Set.of(),
				Set.of());

		assertThat(filter.passesEvent(IN_US, List.of())).isFalse();
		assertThat(filter.passesEvent(DOMESTIC, List.of())).isTrue();
	}

	@Test
	void dislikesAddedLaterJoinTheEarlierOnes() {
		UserFilter filter = UserFilter.of(Facts.interests(List.of(), Set.of(), Set.of(), Set.of()),
				Set.of(new EventId(10)), Set.of())
			.withDisliked(Set.of(new EventId(11)));

		assertThat(filter.passesEvent(DOMESTIC, List.of())).isFalse();
		assertThat(filter.passesEvent(IN_US, List.of())).isFalse();
		assertThat(filter.dislikedEvents()).containsExactlyInAnyOrder(new EventId(10), new EventId(11));
	}

	@Test
	void anEventWhoseTopsAreAllExcludedDoesNotPass() {
		StorylineId excluded = new StorylineId(100);
		StorylineId other = new StorylineId(200);
		UserFilter filter = UserFilter.of(Facts.interests(List.of(), Set.of(), Set.of(), Set.of()), Set.of(),
				Set.of(excluded));

		// 대조 — 꼭대기가 없거나, 거르지 않은 꼭대기가 하나라도 있으면 지난다
		assertThat(filter.passesEvent(DOMESTIC, List.of())).isTrue();
		assertThat(filter.passesEvent(DOMESTIC, List.of(excluded, other))).isTrue();
		assertThat(filter.passesEvent(DOMESTIC, List.of(excluded))).isFalse();
	}

	@Test
	void aBigRowHeadThatIsAnExcludedTopDoesNotPass() {
		StorylineId excluded = new StorylineId(100);
		UserFilter filter = UserFilter.of(Facts.interests(List.of(), Set.of(), Set.of(), Set.of()), Set.of(),
				Set.of(excluded));

		assertThat(filter.passesStoryline(new StorylineId(200))).isTrue();
		assertThat(filter.passesStoryline(excluded)).isFalse();
	}

	@Test
	void dislikesAddedLaterKeepTheExcludedTops() {
		StorylineId excluded = new StorylineId(100);
		UserFilter filter = UserFilter.of(Facts.interests(List.of(), Set.of(), Set.of(), Set.of()), Set.of(),
				Set.of(excluded)).withDisliked(Set.of(new EventId(11)));

		assertThat(filter.excludedStorylines()).containsExactly(excluded);
	}

}
