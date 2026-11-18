package net.yozmnews.digest.domain.interest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleId;
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
 * <li>노출 기사는 평가(활성 중분류)가 있는 것만, 반응은 그 기사의 기록 순 마지막 이력 값 — 노출되지 않은 기사의 피드백은 버린다</li>
 * <li>구독 변경은 원인이 사용자인 행만 관심 신호다 — 계정을 만들 때 기본값 · 체계 변경 · 스토리라인 합쳐짐 · 지움을 따른 고쳐 쓰기는 아니다</li>
 * <li>스토리라인 구독은 지금도 있는 스토리라인(합쳐졌으면 남는 쪽)의 행만 — 지워진 스토리라인은 주제를 모을 수 없다</li>
 * </ul>
 */
public final class InterestSignals {

	private InterestSignals() {
	}

	/**
	 * @param exposures 노출 기사 → 가장 늦은 노출 시각
	 * @param feedback 그 사용자의 이력 행 — 기록 순서
	 * @param subtopics 기사 → 활성 중분류 — 평가가 없는 기사는 없다
	 * @return 노출 기사 가운데 평가가 있는 기사마다 하나
	 */
	public static List<ExposedArticle> exposed(Map<ArticleId, Instant> exposures, List<FeedbackChange> feedback,
			Map<ArticleId, Set<String>> subtopics) {
		Objects.requireNonNull(exposures, "exposures");
		Objects.requireNonNull(feedback, "feedback");
		Objects.requireNonNull(subtopics, "subtopics");
		Map<ArticleId, FeedbackValue> last = new HashMap<>();
		feedback.forEach(change -> last.put(change.article(), change.value()));
		List<ExposedArticle> exposed = new ArrayList<>();
		exposures.forEach((article, at) -> {
			Set<String> topics = subtopics.get(article);
			if (topics != null) {
				exposed.add(new ExposedArticle(topics, at, last.getOrDefault(article, FeedbackValue.NONE)));
			}
		});
		return exposed;
	}

	/** 주제 구독 변경 가운데 관심 신호인 행 — 원인이 사용자이고 푼 중분류가 있는 행, 순서 그대로 */
	public static List<TopicSubscriptionChange> subscriberTopicChanges(List<TopicSubscriptionChange> changes) {
		Objects.requireNonNull(changes, "changes");
		return changes.stream()
			.filter(change -> bySubscriber(change.cause()) && !change.subtopics().isEmpty())
			.toList();
	}

	/** 주제 구독 변경 — {@link #subscriberTopicChanges} 의 행마다, 중분류마다 몫 1 */
	public static List<SubscriptionSignal> topics(List<TopicSubscriptionChange> changes) {
		List<SubscriptionSignal> signals = new ArrayList<>();
		for (TopicSubscriptionChange change : subscriberTopicChanges(changes)) {
			Map<String, Double> shares = new HashMap<>();
			change.subtopics().forEach(subtopic -> shares.put(subtopic, 1.0));
			signals.add(new SubscriptionSignal(shares, sign(change.action()), change.occurredAt()));
		}
		return signals;
	}

	/** 주제를 모을 스토리라인 — 원인이 사용자이고 지금도 있는 행의 지금 스토리라인, 처음 나온 순서로 한 번씩 */
	public static Set<StorylineId> storylinesToRead(List<StorylineSubscriptionChange> changes) {
		Objects.requireNonNull(changes, "changes");
		Set<StorylineId> storylines = new LinkedHashSet<>();
		changes.stream().filter(InterestSignals::counts).forEach(change -> storylines.add(change.current()));
		return storylines;
	}

	/**
	 * 스토리라인 구독 변경 — {@link #storylinesToRead} 와 같은 행 가운데 주제 몫이 있는 행마다.
	 * @param shares 스토리라인 → 주제 몫({@link StorylineTopics#shares})
	 */
	public static List<SubscriptionSignal> storylines(List<StorylineSubscriptionChange> changes,
			Map<StorylineId, Map<String, Double>> shares) {
		Objects.requireNonNull(changes, "changes");
		Objects.requireNonNull(shares, "shares");
		List<SubscriptionSignal> signals = new ArrayList<>();
		for (StorylineSubscriptionChange change : changes) {
			if (!counts(change)) {
				continue;
			}
			Map<String, Double> share = shares.getOrDefault(change.current(), Map.of());
			if (!share.isEmpty()) {
				signals.add(new SubscriptionSignal(share, sign(change.action()), change.occurredAt()));
			}
		}
		return signals;
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
			case STORYLINE_MERGED, STORYLINE_REMOVED -> false;
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
