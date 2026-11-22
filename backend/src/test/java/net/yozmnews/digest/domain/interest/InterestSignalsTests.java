package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.FeedbackChange;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.StorylineSubscriptionCause;
import net.yozmnews.user.StorylineSubscriptionChange;
import net.yozmnews.user.TopicSubscriptionAction;
import net.yozmnews.user.TopicSubscriptionCause;
import net.yozmnews.user.TopicSubscriptionChange;
import org.junit.jupiter.api.Test;

/**
 * 관심 신호로 쓸 행 고르기 — 원인이 사용자인 구독 변경만, 지금도 있는 스토리라인만, 지금 사건마다 기록 순 마지막 반응(기록한 사건이 달라도
 * 같은 사건으로 풀리면 한 줄기), 노출되지 않은 · 지운 사건의 반응은 버림, 주제 없는 노출 사건은 빠짐.
 */
class InterestSignalsTests {

	private static final Instant AT = Instant.parse("2026-11-10T00:00:00Z");

	private static final TagSetId V1 = new TagSetId(1);

	@Test
	void theLastReactionOfEachExposedEventCounts() {
		EventId liked = new EventId(1);
		EventId undone = new EventId(2);
		EventId unrated = new EventId(3);
		EventId untopical = new EventId(4);
		EventId notExposed = new EventId(5);
		Map<EventId, Exposure> exposures = Map.of(liked, new Exposure(AT, 1.0), undone, new Exposure(AT, 0.5), unrated,
				new Exposure(AT, 1.0), untopical, new Exposure(AT, 1.0));
		List<FeedbackChange> feedback = List.of(reaction(liked, FeedbackValue.DISLIKE),
				reaction(undone, FeedbackValue.LIKE), reaction(liked, FeedbackValue.LIKE),
				reaction(undone, FeedbackValue.NONE), reaction(notExposed, FeedbackValue.LIKE));
		Map<EventId, Set<String>> subtopics = Map.of(liked, Set.of("a"), undone, Set.of("b"), unrated, Set.of("c"),
				notExposed, Set.of("d"));

		assertThat(InterestSignals.exposed(exposures, feedback, subtopics)).containsExactlyInAnyOrder(
				new ExposedEvent<>(Map.of("a", 1.0), AT, 1.0, FeedbackValue.LIKE),
				new ExposedEvent<>(Map.of("b", 1.0), AT, 0.5, FeedbackValue.NONE),
				new ExposedEvent<>(Map.of("c", 1.0), AT, 1.0, FeedbackValue.NONE));
	}

	@Test
	void reactionsRecordedOnEventsMergedIntoOneMakeOneStreamAndRemovedOnesDropOut() {
		// 1 · 2 가 2 로 합쳐졌다 — 1 의 좋아요 뒤 2 의 싫어요가 마지막이다. 지운 사건(지금 사건 없음)의 좋아요는 버린다
		EventId survivor = new EventId(2);
		Map<EventId, Exposure> exposures = Map.of(survivor, new Exposure(AT, 1.0));
		List<FeedbackChange> feedback = List.of(
				new FeedbackChange(new EventId(1), survivor, FeedbackValue.LIKE, 1, AT),
				new FeedbackChange(survivor, survivor, FeedbackValue.DISLIKE, 1, AT),
				new FeedbackChange(new EventId(9), null, FeedbackValue.LIKE, 1, AT));

		assertThat(InterestSignals.exposed(exposures, feedback, Map.of(survivor, Set.of("a"))))
			.containsExactly(new ExposedEvent<>(Map.of("a", 1.0), AT, 1.0, FeedbackValue.DISLIKE));
	}

	@Test
	void onlyTopicChangesByTheUserWithResolvedSubtopicsAreSignals() {
		List<TopicSubscriptionChange> changes = List.of(
				topic("economy", Set.of("economy.macro", "economy.markets"), TopicSubscriptionAction.ADD,
						TopicSubscriptionCause.USER),
				topic("politics.executive", Set.of("politics.executive"), TopicSubscriptionAction.REMOVE,
						TopicSubscriptionCause.USER),
				topic("tech.ai", Set.of("tech.ai"), TopicSubscriptionAction.ADD, TopicSubscriptionCause.ONBOARDING),
				topic("tech.game", Set.of("tech.game"), TopicSubscriptionAction.ADD,
						TopicSubscriptionCause.TAG_SET_CHANGE),
				topic("economy.crypto", Set.of(), TopicSubscriptionAction.ADD, TopicSubscriptionCause.USER));

		assertThat(InterestSignals.topics(changes)).containsExactly(
				new SubscriptionSignal<>(Map.of("economy.macro", 1.0, "economy.markets", 1.0), 1, AT),
				new SubscriptionSignal<>(Map.of("politics.executive", 1.0), -1, AT));
	}

	@Test
	void onlyStorylineChangesByTheUserOnStorylinesThatStillExistAreRead() {
		StorylineId kept = new StorylineId(10);
		StorylineId survivor = new StorylineId(20);
		List<StorylineSubscriptionChange> changes = List.of(
				storyline(kept, kept, StorylineSubscriptionAction.ADD, StorylineSubscriptionCause.USER),
				storyline(new StorylineId(11), survivor, StorylineSubscriptionAction.REMOVE,
						StorylineSubscriptionCause.USER),
				storyline(new StorylineId(12), null, StorylineSubscriptionAction.ADD, StorylineSubscriptionCause.USER),
				storyline(new StorylineId(13), new StorylineId(30), StorylineSubscriptionAction.ADD,
						StorylineSubscriptionCause.STORYLINE_MERGED),
				storyline(new StorylineId(14), null, StorylineSubscriptionAction.REMOVE,
						StorylineSubscriptionCause.STORYLINE_REMOVED),
				storyline(kept, kept, StorylineSubscriptionAction.REMOVE, StorylineSubscriptionCause.USER));

		assertThat(InterestSignals.storylinesToRead(changes)).containsExactly(kept, survivor);
		assertThat(InterestSignals.storylines(changes, Map.of(kept, Map.of("a", 0.5), survivor, Map.of())))
			.as("몫이 빈 스토리라인은 신호가 아니다")
			.containsExactly(new SubscriptionSignal<>(Map.of("a", 0.5), 1, AT),
					new SubscriptionSignal<>(Map.of("a", 0.5), -1, AT));
	}

	/** 그대로인 사건의 이력 행 — 기록한 사건과 지금 사건이 같다 */
	private static FeedbackChange reaction(EventId event, FeedbackValue value) {
		return new FeedbackChange(event, event, value, 1, AT);
	}

	private static TopicSubscriptionChange topic(String key, Set<String> subtopics, TopicSubscriptionAction action,
			TopicSubscriptionCause cause) {
		return new TopicSubscriptionChange(key, V1, subtopics, action, cause, AT);
	}

	private static StorylineSubscriptionChange storyline(StorylineId recorded, StorylineId current,
			StorylineSubscriptionAction action, StorylineSubscriptionCause cause) {
		return new StorylineSubscriptionChange(recorded, current, action, cause, AT);
	}

}
