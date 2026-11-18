package net.yozmnews.user.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.domain.subscription.StorylineSubscriptionHistoryPage;
import net.yozmnews.user.domain.subscription.StorylineSubscriptionRepository;
import net.yozmnews.user.domain.subscription.SubscriptionChangeCursor;
import net.yozmnews.user.domain.subscription.UserSubscriptionChange;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 구독 이력 — 세션 사용자가 직접 한 구독 · 해제를 최근부터 한 쪽씩. docs/specs/user.md §4 · user-R63
 * <p>
 * 소비자 조회({@code StorylineSubscriptionDirectory})를 거치지 않고 저장소에서 원인이 사용자인 행만 읽는다. 기록된 ID 는
 * {@link CurrentStorylines} 로 지금 스토리라인으로 푼다. 쓰지 않는다 — 설정 행을 잠그지 않고 구독을 따라가지 않는다.
 */
@Service
public class StorylineSubscriptionHistory {

	private final StorylineSubscriptionRepository subscriptions;

	private final CurrentStorylines current;

	StorylineSubscriptionHistory(StorylineSubscriptionRepository subscriptions, CurrentStorylines current) {
		this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
		this.current = Objects.requireNonNull(current, "current");
	}

	/**
	 * 한 쪽 — {@code before} 가 없으면 가장 최근 행부터.
	 * @param before 앞 쪽이 낸 다음 쪽 커서 — 이보다 앞선 행부터 낸다
	 */
	@Transactional(readOnly = true)
	public HistoryPage page(UserId user, @Nullable SubscriptionChangeCursor before) {
		int size = StorylineSubscriptionHistoryPage.SIZE;
		List<UserSubscriptionChange> rows = this.subscriptions.userChanges(user, before, size + 1);
		List<UserSubscriptionChange> shown = rows.subList(0, Math.min(size, rows.size()));
		Map<Long, Long> resolved = this.current
			.resolve(shown.stream().map(UserSubscriptionChange::storylineId).toList());
		List<HistoryEntry> entries = shown.stream().map(row -> {
			Long now = resolved.get(row.storylineId());
			return new HistoryEntry((now == null) ? null : new StorylineId(now), row.action(), row.occurredAt());
		}).toList();
		SubscriptionChangeCursor next = (rows.size() > size) ? shown.getLast().cursor() : null;
		return new HistoryPage(entries, next);
	}

	/**
	 * 내 구독 이력 한 쪽.
	 *
	 * @param entries 최근부터
	 * @param next 다음 쪽 커서 — 더 앞선 행이 없으면 null
	 */
	public record HistoryPage(List<HistoryEntry> entries, @Nullable SubscriptionChangeCursor next) {

		public HistoryPage {
			entries = List.copyOf(entries);
		}

	}

	/**
	 * 구독 · 해제 하나.
	 *
	 * @param current 그 행의 스토리라인을 지금 푼 것(합쳐졌으면 남는 쪽) — 지워졌으면 null
	 * @param action 구독(더함) · 해제(뺌)
	 * @param occurredAt 기록한 시각
	 */
	public record HistoryEntry(@Nullable StorylineId current, StorylineSubscriptionAction action, Instant occurredAt) {

		public HistoryEntry {
			Objects.requireNonNull(action, "action");
			Objects.requireNonNull(occurredAt, "occurredAt");
		}

	}

}
