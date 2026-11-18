package net.yozmnews.user.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.StorylineSubscriptionChange;
import net.yozmnews.user.StorylineSubscriptionDirectory;
import net.yozmnews.user.domain.subscription.StorylineSubscriptionRepository;
import net.yozmnews.user.domain.subscription.StoredSubscriptionChange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link StorylineSubscriptionDirectory} 의 구현 — 저장된 구독 · 이력을 읽어 {@link CurrentStorylines} 로 남는 쪽으로 푼다. 쓰지 않는다.
 * docs/specs/user.md §4 · user-R55
 */
@Service
class StorylineSubscriptionDirectoryService implements StorylineSubscriptionDirectory {

	private final StorylineSubscriptionRepository subscriptions;

	private final CurrentStorylines current;

	StorylineSubscriptionDirectoryService(StorylineSubscriptionRepository subscriptions, CurrentStorylines current) {
		this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
		this.current = Objects.requireNonNull(current, "current");
	}

	@Override
	@Transactional(readOnly = true)
	public List<StorylineId> subscriptions(UserId user) {
		TreeSet<Long> ids = new TreeSet<>(this.current.resolve(this.subscriptions.load(user).keySet()).values());
		return ids.stream().map(StorylineId::new).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<StorylineSubscriptionChange> changes(UserId user, Instant from, Instant to) {
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("기간의 시작이 끝보다 늦다: " + from + " · " + to);
		}
		List<StoredSubscriptionChange> stored = this.subscriptions.changes(user, from, to);
		Map<Long, Long> resolved = this.current
			.resolve(stored.stream().map(StoredSubscriptionChange::storylineId).toList());
		return stored.stream().map(change -> {
			Long current = resolved.get(change.storylineId());
			return new StorylineSubscriptionChange(new StorylineId(change.storylineId()),
					(current == null) ? null : new StorylineId(current), change.action(), change.cause(),
					change.occurredAt());
		}).toList();
	}

}
