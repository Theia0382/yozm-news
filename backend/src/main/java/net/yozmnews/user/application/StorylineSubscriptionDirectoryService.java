package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.StorylineSubscriptionChange;
import net.yozmnews.user.StorylineSubscriptionDirectory;
import net.yozmnews.user.domain.subscription.StorylineSubscriptionRepository;
import net.yozmnews.user.domain.subscription.StoredSubscriptionChange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link StorylineSubscriptionDirectory} 의 구현 — 저장된 구독 · 이력을 읽어 {@code storyline} 의 남는 쪽 풀기로 푼다. 쓰지 않는다.
 * docs/specs/user.md §4 · user-R55
 */
@Service
class StorylineSubscriptionDirectoryService implements StorylineSubscriptionDirectory {

	private final StorylineSubscriptionRepository subscriptions;

	private final StorylineDirectory storylines;

	StorylineSubscriptionDirectoryService(StorylineSubscriptionRepository subscriptions, StorylineDirectory storylines) {
		this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
		this.storylines = Objects.requireNonNull(storylines, "storylines");
	}

	@Override
	@Transactional(readOnly = true)
	public List<StorylineId> subscriptions(UserId user) {
		TreeSet<Long> current = new TreeSet<>();
		resolve(this.subscriptions.load(user).keySet()).values().forEach(current::add);
		return current.stream().map(StorylineId::new).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<StorylineSubscriptionChange> changes(UserId user, Instant from, Instant to) {
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("기간의 시작이 끝보다 늦다: " + from + " · " + to);
		}
		List<StoredSubscriptionChange> stored = this.subscriptions.changes(user, from, to);
		Map<Long, Long> resolved = resolve(stored.stream().map(StoredSubscriptionChange::storylineId).toList());
		return stored.stream().map(change -> {
			Long current = resolved.get(change.storylineId());
			return new StorylineSubscriptionChange(new StorylineId(change.storylineId()),
					(current == null) ? null : new StorylineId(current), change.action(), change.cause(),
					change.occurredAt());
		}).toList();
	}

	/** 받은 ID → 지금 가리키는 스토리라인 ID — 사라진 ID 는 빠진다 */
	private Map<Long, Long> resolve(Collection<Long> ids) {
		Map<Long, Long> resolved = new HashMap<>();
		if (ids.isEmpty()) {
			return resolved;
		}
		this.storylines.findAll(ids.stream().distinct().map(StorylineId::new).toList())
			.forEach((requested, view) -> resolved.put(requested.value(), view.id().value()));
		return resolved;
	}

}
