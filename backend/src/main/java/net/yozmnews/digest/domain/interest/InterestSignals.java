package net.yozmnews.digest.domain.interest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.FeedbackChange;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.StorylineSubscriptionCause;
import net.yozmnews.user.StorylineSubscriptionChange;
import net.yozmnews.user.TopicSubscriptionAction;
import net.yozmnews.user.TopicSubscriptionCause;
import net.yozmnews.user.TopicSubscriptionChange;

/**
 * 읽은 이력 행에서 관심도 산식의 입력을 고르는 정본 — docs/specs/digest.md §5
 * <ul>
 * <li>노출 사건은 주제 쪽과 스토리라인 쪽으로 나뉜다 — 주제 쪽은 주제(활성 중분류)를 셀 수 있고 반복이 아닌 사건({@link StorylineRepeats}),
 * 스토리라인 쪽은 꼭대기가 있는 사건(꼭대기마다 몫 배수 1/k). 꼭대기가 없는 사건은 주제 쪽에만, 처음 본 흐름의 사건은 양쪽에, 반복인 사건은
 * 스토리라인 쪽에만 든다</li>
 * <li>반응은 지금 사건이 그 사건인 이력 행의 기록 순 마지막 값 — 기록한 사건이 달라도 같은 사건으로 풀리면 한 줄기다. 노출되지 않은 사건 · 지운
 * 사건의 반응은 버린다. 두 쪽이 같은 고르기를 쓴다</li>
 * <li>구독 변경은 원인이 사용자인 행만 관심 신호다 — 계정을 만들 때 기본값 · 체계 변경 · 스토리라인 합쳐짐 · 지움을 따른 고쳐 쓰기는 아니다</li>
 * <li>스토리라인 구독은 지금도 있는 스토리라인(합쳐졌으면 남는 쪽)의 행만, 그 스토리라인의 꼭대기 하나에 몫 1 로 든다 — 주제에는 들지 않는다</li>
 * </ul>
 */
public final class InterestSignals {

	private InterestSignals() {
	}

	/**
	 * 주제 쪽 노출 사건
	 * @param exposures 지금 사건 → 창 안 가장 늦은 노출(시각 · 몫)
	 * @param feedback 그 사용자의 이력 행 — 기록 순서
	 * @param subtopics 지금 사건 → 활성 중분류 — 주제를 셀 수 없는 사건은 없다
	 * @param repeated 반복인 사건({@link StorylineRepeats#repeated})
	 * @return 주제를 셀 수 있고 반복이 아닌 노출 사건마다 하나 — 주제마다 몫 배수 1
	 */
	public static List<ExposedEvent<String>> topicExposed(Map<EventId, Exposure> exposures,
			List<FeedbackChange> feedback, Map<EventId, Set<String>> subtopics, Set<EventId> repeated) {
		Objects.requireNonNull(exposures, "exposures");
		Objects.requireNonNull(subtopics, "subtopics");
		Objects.requireNonNull(repeated, "repeated");
		Map<EventId, FeedbackValue> last = lastReactions(feedback);
		List<ExposedEvent<String>> exposed = new ArrayList<>();
		exposures.forEach((event, exposure) -> {
			Set<String> topics = subtopics.get(event);
			if (topics != null && !repeated.contains(event)) {
				exposed.add(new ExposedEvent<>(each(topics), exposure.at(), exposure.share(),
						last.getOrDefault(event, FeedbackValue.NONE)));
			}
		});
		return exposed;
	}

	/**
	 * 스토리라인 쪽 노출 사건
	 * @param exposures 지금 사건 → 창 안 가장 늦은 노출(시각 · 몫)
	 * @param feedback 그 사용자의 이력 행 — 기록 순서
	 * @param tops 지금 사건 → 꼭대기 — 소속이 없는 사건은 없다
	 * @return 꼭대기가 있는 노출 사건마다 하나 — 꼭대기 k 개면 꼭대기마다 몫 배수 1/k. 주제를 셀 수 없는 사건도 든다
	 */
	public static List<ExposedEvent<StorylineId>> storylineExposed(Map<EventId, Exposure> exposures,
			List<FeedbackChange> feedback, Map<EventId, List<StorylineId>> tops) {
		Objects.requireNonNull(exposures, "exposures");
		Objects.requireNonNull(tops, "tops");
		Map<EventId, FeedbackValue> last = lastReactions(feedback);
		List<ExposedEvent<StorylineId>> exposed = new ArrayList<>();
		exposures.forEach((event, exposure) -> {
			List<StorylineId> roots = tops.get(event);
			if (roots != null && !roots.isEmpty()) {
				Map<StorylineId, Double> keys = new HashMap<>();
				roots.forEach(top -> keys.put(top, 1.0 / roots.size()));
				exposed.add(new ExposedEvent<>(keys, exposure.at(), exposure.share(),
						last.getOrDefault(event, FeedbackValue.NONE)));
			}
		});
		return exposed;
	}

	/** 지금 사건마다 기록 순 마지막 값 — 지운 사건(지금 사건 없음)의 행은 버린다 */
	private static Map<EventId, FeedbackValue> lastReactions(List<FeedbackChange> feedback) {
		Objects.requireNonNull(feedback, "feedback");
		Map<EventId, FeedbackValue> last = new HashMap<>();
		for (FeedbackChange change : feedback) {
			if (change.current() != null) {
				last.put(change.current(), change.value());
			}
		}
		return last;
	}

	/** 주제 구독 변경 가운데 관심 신호인 행 — 원인이 사용자이고 푼 중분류가 있는 행, 순서 그대로 */
	public static List<TopicSubscriptionChange> subscriberTopicChanges(List<TopicSubscriptionChange> changes) {
		Objects.requireNonNull(changes, "changes");
		return changes.stream()
			.filter(change -> bySubscriber(change.cause()) && !change.subtopics().isEmpty())
			.toList();
	}

	/** 주제 구독 변경 — {@link #subscriberTopicChanges} 의 행마다, 중분류마다 몫 1 */
	public static List<SubscriptionSignal<String>> topics(List<TopicSubscriptionChange> changes) {
		List<SubscriptionSignal<String>> signals = new ArrayList<>();
		for (TopicSubscriptionChange change : subscriberTopicChanges(changes)) {
			signals.add(new SubscriptionSignal<>(each(change.subtopics()), sign(change.action()), change.occurredAt()));
		}
		return signals;
	}

	/** 꼭대기를 읽을 스토리라인 — 원인이 사용자이고 지금도 있는 행의 지금 스토리라인, 처음 나온 순서로 한 번씩 */
	public static Set<StorylineId> storylinesToRead(List<StorylineSubscriptionChange> changes) {
		Objects.requireNonNull(changes, "changes");
		Set<StorylineId> storylines = new LinkedHashSet<>();
		changes.stream().filter(InterestSignals::counts).forEach(change -> storylines.add(change.current()));
		return storylines;
	}

	/**
	 * 스토리라인 구독 변경 — {@link #storylinesToRead} 와 같은 행 가운데 꼭대기를 아는 행마다, 그 꼭대기에 몫 1.
	 * @param storylineTops 지금 스토리라인 → 꼭대기 — 없는 스토리라인은 없다
	 */
	public static List<SubscriptionSignal<StorylineId>> storylines(List<StorylineSubscriptionChange> changes,
			Map<StorylineId, StorylineId> storylineTops) {
		Objects.requireNonNull(changes, "changes");
		Objects.requireNonNull(storylineTops, "storylineTops");
		List<SubscriptionSignal<StorylineId>> signals = new ArrayList<>();
		for (StorylineSubscriptionChange change : changes) {
			if (!counts(change)) {
				continue;
			}
			StorylineId top = storylineTops.get(change.current());
			if (top != null) {
				signals.add(new SubscriptionSignal<>(Map.of(top, 1.0), sign(change.action()), change.occurredAt()));
			}
		}
		return signals;
	}

	/** 키마다 몫 1 — 주제 중분류는 사건의 주제 · 구독이 덮는 중분류마다 온전히 든다 */
	private static Map<String, Double> each(Set<String> subtopics) {
		Map<String, Double> shares = new HashMap<>();
		subtopics.forEach(subtopic -> shares.put(subtopic, 1.0));
		return shares;
	}

	private static boolean counts(StorylineSubscriptionChange change) {
		return bySubscriber(change.cause()) && change.current() != null;
	}

	private static boolean bySubscriber(TopicSubscriptionCause cause) {
		return switch (cause) {
			case USER -> true;
			case ONBOARDING, TAG_SET_CHANGE -> false;
		};
	}

	private static boolean bySubscriber(StorylineSubscriptionCause cause) {
		return switch (cause) {
			case USER -> true;
			case STORYLINE_MERGED, STORYLINE_REMOVED, STORYLINE_SPLIT -> false;
		};
	}

	private static int sign(TopicSubscriptionAction action) {
		return switch (action) {
			case ADD -> 1;
			case REMOVE -> -1;
		};
	}

	private static int sign(StorylineSubscriptionAction action) {
		return switch (action) {
			case ADD -> 1;
			case REMOVE -> -1;
		};
	}

}
