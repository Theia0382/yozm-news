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

		assertThat(InterestSignals.topicExposed(exposures, feedback, subtopics, Set.of())).containsExactlyInAnyOrder(
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

		assertThat(InterestSignals.topicExposed(exposures, feedback, Map.of(survivor, Set.of("a")), Set.of()))
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
		// kept 의 꼭대기는 top, survivor 는 꼭대기를 모른다(그 사이 지워짐) — 꼭대기에 몫 1 로 든다
		StorylineId top = new StorylineId(100);
		assertThat(InterestSignals.storylines(changes, Map.of(kept, top), new StorylineFlows(Map.of(top, Set.of(7)), Set.of())))
			.as("꼭대기를 모르는 스토리라인은 신호가 아니다 — 신호는 그 꼭대기의 묶음에 든다")
			.containsExactly(new SubscriptionSignal<>(Map.of(7, 1.0), 1, AT),
					new SubscriptionSignal<>(Map.of(7, 1.0), -1, AT));
		// 대조 — 꼭대기에 묶음이 없으면 신호가 아니다
		assertThat(InterestSignals.storylines(changes, Map.of(kept, top), StorylineFlows.NONE)).isEmpty();
	}

	@Test
	void aRepeatedEventStaysOutOfTheTopicSide() {
		EventId first = new EventId(1);
		EventId repeated = new EventId(2);
		Map<EventId, Exposure> exposures = Map.of(first, new Exposure(AT, 1.0), repeated, new Exposure(AT, 1.0));
		Map<EventId, Set<String>> subtopics = Map.of(first, Set.of("a"), repeated, Set.of("a"));

		// 대조 — 반복이 없으면 둘 다 주제 쪽
		assertThat(InterestSignals.topicExposed(exposures, List.of(), subtopics, Set.of())).hasSize(2);
		assertThat(InterestSignals.topicExposed(exposures, List.of(reaction(repeated, FeedbackValue.DISLIKE)),
				subtopics, Set.of(repeated)))
			.containsExactly(new ExposedEvent<>(Map.of("a", 1.0), AT, 1.0, FeedbackValue.NONE));
	}

	@Test
	void theStorylineSideSplitsAnEventAmongItsTopsAndTakesEventsWithoutTopics() {
		EventId twoTrees = new EventId(1);
		EventId untopical = new EventId(2);
		EventId untreed = new EventId(3);
		StorylineId tree = new StorylineId(100);
		StorylineId other = new StorylineId(200);
		Map<EventId, Exposure> exposures = Map.of(twoTrees, new Exposure(AT, 1.0), untopical, new Exposure(AT, 0.5),
				untreed, new Exposure(AT, 1.0));
		Map<EventId, List<StorylineId>> tops = Map.of(twoTrees, List.of(tree, other), untopical, List.of(tree));

		List<FeedbackChange> liked = List.of(reaction(twoTrees, FeedbackValue.LIKE));
		// 꼭대기마다 다른 묶음 — 꼭대기 둘이면 묶음마다 1/2
		assertThat(InterestSignals.storylineExposed(exposures, liked, tops,
				new StorylineFlows(Map.of(tree, Set.of(0), other, Set.of(1)), Set.of())))
			.containsExactlyInAnyOrder(new ExposedEvent<>(Map.of(0, 0.5, 1, 0.5), AT, 1.0, FeedbackValue.LIKE),
					new ExposedEvent<>(Map.of(0, 1.0), AT, 0.5, FeedbackValue.NONE));
		// 두 꼭대기가 같은 묶음(이어진 흐름) — 몫이 그 묶음에 모인다
		assertThat(InterestSignals.storylineExposed(exposures, liked, tops,
				new StorylineFlows(Map.of(tree, Set.of(0), other, Set.of(0)), Set.of())))
			.containsExactlyInAnyOrder(new ExposedEvent<>(Map.of(0, 1.0), AT, 1.0, FeedbackValue.LIKE),
					new ExposedEvent<>(Map.of(0, 1.0), AT, 0.5, FeedbackValue.NONE));
		// 묶음이 없는 꼭대기의 몫은 버린다 — tree 에만 묶음이 있으면 twoTrees 는 1/2 만 든다
		assertThat(InterestSignals.storylineExposed(exposures, liked, tops, new StorylineFlows(Map.of(tree, Set.of(0)), Set.of())))
			.containsExactlyInAnyOrder(new ExposedEvent<>(Map.of(0, 0.5), AT, 1.0, FeedbackValue.LIKE),
					new ExposedEvent<>(Map.of(0, 1.0), AT, 0.5, FeedbackValue.NONE));
		// 대조 — 묶음이 하나도 없으면 스토리라인 쪽 재료가 없다
		assertThat(InterestSignals.storylineExposed(exposures, liked, tops, StorylineFlows.NONE)).isEmpty();
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


	@Test
	void aTopInSeveralFlowsCarriesItsShareAndSubscriptionToEachFlow() {
		// 사건 하나의 꼭대기 5 가 묶음 0 · 1 에 든다 → 두 묶음에 각각 몫 배수 1, 스토리라인 5 의 추적 더함도 두 묶음에 각각 1
		StorylineId shared = new StorylineId(5);
		StorylineFlows flows = new StorylineFlows(Map.of(shared, Set.of(0, 1)), Set.of());
		EventId event = new EventId(41);
		Instant at = Instant.parse("2026-11-20T00:00:00Z");

		List<ExposedEvent<Integer>> exposed = InterestSignals.storylineExposed(Map.of(event, new Exposure(at, 1.0)),
				List.of(), Map.of(event, List.of(shared)), flows);
		assertThat(exposed).singleElement().satisfies(e -> assertThat(e.keys()).isEqualTo(Map.of(0, 1.0, 1, 1.0)));

		StorylineId tracked = new StorylineId(9);
		List<StorylineSubscriptionChange> changes = List.of(
				storyline(tracked, tracked, StorylineSubscriptionAction.ADD, StorylineSubscriptionCause.USER));
		assertThat(InterestSignals.storylines(changes, Map.of(tracked, shared), flows))
			.containsExactly(new SubscriptionSignal<>(Map.of(0, 1.0, 1, 1.0), 1, AT));
	}
}
