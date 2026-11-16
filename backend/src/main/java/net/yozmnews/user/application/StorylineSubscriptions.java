package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineState;
import net.yozmnews.storyline.StorylineView;
import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.StorylineSubscriptionCause;
import net.yozmnews.user.domain.subscription.StorylineSubscriptionLimit;
import net.yozmnews.user.domain.subscription.StorylineSubscriptionRepository;
import net.yozmnews.user.domain.subscription.SubscriptionChange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스토리라인 구독 조회 · 구독 · 해제 — 세션 사용자의 것. docs/specs/user.md §4 · user-R52 · user-R53
 * <p>
 * 셋 모두 한 트랜잭션에서 설정 행을 잠그고(관심 설정과 같은 줄 — user-R46) 먼저 구독을 따라간 뒤 한다. 그래서 행은 모두 지금 남는
 * 스토리라인 ID 다. 구독은 받은 ID 를 남는 스토리라인으로 풀어 저장하고, 바뀌지 않으면 이력을 남기지 않는다. 이력의 원인은 사용자다.
 */
@Service
public class StorylineSubscriptions {

	private final InterestSettingsLock lock;

	private final SubscriptionFollowups followups;

	private final StorylineSubscriptionRepository subscriptions;

	private final StorylineDirectory storylines;

	StorylineSubscriptions(InterestSettingsLock lock, SubscriptionFollowups followups,
			StorylineSubscriptionRepository subscriptions, StorylineDirectory storylines) {
		this.lock = Objects.requireNonNull(lock, "lock");
		this.followups = Objects.requireNonNull(followups, "followups");
		this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
		this.storylines = Objects.requireNonNull(storylines, "storylines");
	}

	/** 내 구독 — 구독 시각 내림차순, 같으면 스토리라인 ID 순. 지금 상태를 함께 싣는다 */
	@Transactional
	public SubscriptionsView view(UserId user, Instant now) {
		Map<Long, Instant> rows = followed(user, now);
		Map<StorylineId, StorylineView> views = this.storylines
			.findAll(rows.keySet().stream().map(StorylineId::new).toList());
		List<SubscribedStoryline> subscribed = rows.entrySet()
			.stream()
			.filter(row -> views.containsKey(new StorylineId(row.getKey())))
			.map(row -> new SubscribedStoryline(new StorylineId(row.getKey()), row.getValue(),
					views.get(new StorylineId(row.getKey())).state()))
			.sorted(Comparator.comparing(SubscribedStoryline::subscribedAt)
				.reversed()
				.thenComparing(subscription -> subscription.id().value()))
			.toList();
		return new SubscriptionsView(subscribed, StorylineSubscriptionLimit.MAX);
	}

	/** 구독 — 받은 ID 가 합쳐졌으면 남는 스토리라인을 구독한다 */
	@Transactional
	public SubscribeOutcome subscribe(UserId user, StorylineId storyline, Instant now) {
		Map<Long, Instant> rows = followed(user, now);
		StorylineView found = this.storylines.findAll(List.of(storyline)).get(storyline);
		if (found == null) {
			return new SubscribeOutcome.UnknownStoryline();
		}
		StorylineId survivor = found.id();
		if (rows.containsKey(survivor.value())) {
			return new SubscribeOutcome.AlreadySubscribed(survivor);
		}
		if (rows.size() >= StorylineSubscriptionLimit.MAX) {
			return new SubscribeOutcome.LimitReached(StorylineSubscriptionLimit.MAX);
		}
		this.subscriptions.add(user, survivor.value(), now);
		this.subscriptions.record(user,
				List.of(new SubscriptionChange(survivor.value(), StorylineSubscriptionAction.ADD)),
				StorylineSubscriptionCause.USER, now);
		return new SubscribeOutcome.Subscribed(survivor);
	}

	/** 해제 — 받은 ID 가 합쳐졌으면 남는 스토리라인의 구독을 푼다 */
	@Transactional
	public UnsubscribeOutcome unsubscribe(UserId user, StorylineId storyline, Instant now) {
		followed(user, now);
		StorylineView found = this.storylines.findAll(List.of(storyline)).get(storyline);
		StorylineId target = (found == null) ? storyline : found.id();
		if (!this.subscriptions.remove(user, target.value())) {
			return new UnsubscribeOutcome.NotSubscribed();
		}
		this.subscriptions.record(user,
				List.of(new SubscriptionChange(target.value(), StorylineSubscriptionAction.REMOVE)),
				StorylineSubscriptionCause.USER, now);
		return new UnsubscribeOutcome.Unsubscribed(target);
	}

	/** 설정 행을 만들고 · 잠그고 · 태그 선호와 구독을 따라간 뒤의 구독 행 */
	private Map<Long, Instant> followed(UserId user, Instant now) {
		this.lock.lockFollowed(user, now);
		return this.followups.followLocked(user, Map.of(), now);
	}

	/**
	 * 내 구독.
	 *
	 * @param subscriptions 구독 시각 내림차순
	 * @param limit 구독 상한
	 */
	public record SubscriptionsView(List<SubscribedStoryline> subscriptions, int limit) {

		public SubscriptionsView {
			subscriptions = List.copyOf(subscriptions);
		}

	}

	/**
	 * 구독 하나.
	 *
	 * @param id 스토리라인
	 * @param subscribedAt 처음 구독한 시각(합쳐져 옮겼으면 옮긴 행의 것)
	 * @param state 조회 시각의 상태 — 끝나도 구독은 남는다
	 */
	public record SubscribedStoryline(StorylineId id, Instant subscribedAt, StorylineState state) {
	}

	/** 구독의 결과 */
	public sealed interface SubscribeOutcome {

		/** 구독했다 — 남는 스토리라인 */
		record Subscribed(StorylineId id) implements SubscribeOutcome {
		}

		/** 이미 구독했다 — 바꾸지 않았다 */
		record AlreadySubscribed(StorylineId id) implements SubscribeOutcome {
		}

		/** 없는 스토리라인이다(지웠거나 처음부터 없는 ID) */
		record UnknownStoryline() implements SubscribeOutcome {
		}

		/** 구독 수가 상한이다 — 바꾸지 않았다 */
		record LimitReached(int limit) implements SubscribeOutcome {
		}

	}

	/** 해제의 결과 */
	public sealed interface UnsubscribeOutcome {

		/** 해제했다 */
		record Unsubscribed(StorylineId id) implements UnsubscribeOutcome {
		}

		/** 구독하지 않은 스토리라인이다 — 바꾸지 않았다 */
		record NotSubscribed() implements UnsubscribeOutcome {
		}

	}

}
