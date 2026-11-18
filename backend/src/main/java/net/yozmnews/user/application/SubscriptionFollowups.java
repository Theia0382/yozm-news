package net.yozmnews.user.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.RetiredStoryline;
import net.yozmnews.storyline.StorylinesRetired;
import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.StorylineSubscriptionCause;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import net.yozmnews.user.domain.notice.NoticeRepository;
import net.yozmnews.user.domain.notice.RemovedSubscription;
import net.yozmnews.user.domain.notice.StorylineSubscriptionsRemovedContent;
import net.yozmnews.user.domain.subscription.StorylineSubscriptionRepository;
import net.yozmnews.user.domain.subscription.SubscriptionChange;
import net.yozmnews.user.domain.subscription.SubscriptionFollow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 구독 따라가기 — 사용자의 스토리라인 구독을 지금 가리키는 스토리라인으로 고쳐 쓴다. docs/specs/user.md §4 · user-R53 · user-R54
 * <p>
 * 사용자 하나를 고쳐 쓰는 정본은 {@link #followLocked} 하나다 — 리스너({@link #followRetired})와 구독 조회 · 저장 유스케이스가 함께
 * 쓴다. 판정은 {@link SubscriptionFollow} 가 하고, 남는 쪽 풀기는 {@link CurrentStorylines} 가 한다.
 * 합쳐져 옮긴 행은 옛 ID 의 뺌과(새로 더했으면) 남는 ID 의 더함, 사라진 행은 뺌으로 이력에 남기고, 사라진 구독이 있으면 알림 하나를
 * 남긴다. 알림은 스토리라인 ID 와 풀기의 남은 사건만 담는다 — 기사를 읽지 않는다.
 */
@Service
class SubscriptionFollowups {

	private static final Logger logger = LoggerFactory.getLogger(SubscriptionFollowups.class);

	/** 한 번에 읽는 사용자 수 */
	static final int BATCH = 100;

	private final StorylineSubscriptionRepository subscriptions;

	private final InterestSettingsRepository settings;

	private final NoticeRepository notices;

	private final CurrentStorylines storylines;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	SubscriptionFollowups(StorylineSubscriptionRepository subscriptions, InterestSettingsRepository settings,
			NoticeRepository notices, CurrentStorylines storylines, TransactionTemplate transaction) {
		this(subscriptions, settings, notices, storylines, transaction, InstantSource.system());
	}

	SubscriptionFollowups(StorylineSubscriptionRepository subscriptions, InterestSettingsRepository settings,
			NoticeRepository notices, CurrentStorylines storylines, TransactionTemplate transaction,
			InstantSource clock) {
		this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
		this.settings = Objects.requireNonNull(settings, "settings");
		this.notices = Objects.requireNonNull(notices, "notices");
		this.storylines = Objects.requireNonNull(storylines, "storylines");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * 설정 행을 잠근 사용자의 구독을 따라가게 한다 — 부르는 쪽의 트랜잭션 안에서만. 바뀐 것이 없으면 아무것도 쓰지 않는다.
	 * @param remainingEvents 받은 이벤트가 실은 풀기의 남은 사건(스토리라인 ID → 사건 ID) — 이벤트 없이 부르면 빈 맵
	 * @return 따라간 뒤의 구독 행 — 스토리라인 ID → 구독 시각
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	Map<Long, Instant> followLocked(UserId user, Map<Long, Long> remainingEvents, Instant now) {
		return follow(user, remainingEvents, now).rows();
	}

	private Followed follow(UserId user, Map<Long, Long> remainingEvents, Instant now) {
		Map<Long, Instant> current = this.subscriptions.load(user);
		if (current.isEmpty()) {
			return new Followed(current, false);
		}
		Map<Long, Long> resolved = this.storylines.resolve(current.keySet());
		SubscriptionFollow follow = SubscriptionFollow.decide(current, resolved);
		if (!follow.changed()) {
			return new Followed(current, false);
		}
		this.subscriptions.replace(user, follow.next());
		List<SubscriptionChange> merged = new ArrayList<>();
		for (SubscriptionFollow.Move move : follow.moved()) {
			merged.add(new SubscriptionChange(move.from(), StorylineSubscriptionAction.REMOVE));
			if (move.added()) {
				merged.add(new SubscriptionChange(move.to(), StorylineSubscriptionAction.ADD));
			}
		}
		this.subscriptions.record(user, merged, StorylineSubscriptionCause.STORYLINE_MERGED, now);
		this.subscriptions.record(user,
				follow.removed()
					.stream()
					.map(id -> new SubscriptionChange(id, StorylineSubscriptionAction.REMOVE))
					.toList(),
				StorylineSubscriptionCause.STORYLINE_REMOVED, now);
		if (!follow.removed().isEmpty()) {
			this.notices.create(user, new StorylineSubscriptionsRemovedContent(follow.removed()
				.stream()
				.map(id -> new RemovedSubscription(id, remainingEvents.get(id)))
				.toList()), now);
		}
		return new Followed(follow.next(), true);
	}

	/**
	 * 합치거나 지운 스토리라인을 구독한 사용자를 모두 따라가게 한다 — 트랜잭션 밖에서 부른다. 사용자마다 트랜잭션 하나라 한 사용자의 실패는 그
	 * 사용자만 되돌리고 예외는 그대로 올린다(이벤트 재처리가 남은 사용자를 고친다). 설정 행이 없는 사용자는 건너뛴다 — 구독은 설정 행을 만든
	 * 뒤에만 생긴다.
	 * @return 구독이 바뀐 사용자 수
	 */
	int followRetired(StorylinesRetired retired) {
		List<Long> ids = new ArrayList<>();
		Map<Long, Long> remainingEvents = new HashMap<>();
		for (RetiredStoryline storyline : retired.storylines()) {
			ids.add(storyline.id().value());
			if (storyline.remainingEvent() != null) {
				remainingEvents.put(storyline.id().value(), storyline.remainingEvent().value());
			}
		}
		int followed = 0;
		long after = 0;
		while (true) {
			List<UserId> batch = this.subscriptions.subscribersOf(ids, after, BATCH);
			if (batch.isEmpty()) {
				break;
			}
			for (UserId user : batch) {
				Boolean changed = this.transaction.execute(status -> this.settings.lockByUser(user)
					.map(locked -> follow(user, remainingEvents, this.clock.instant()).changed())
					.orElse(false));
				if (Boolean.TRUE.equals(changed)) {
					followed++;
				}
				after = user.value();
			}
		}
		if (followed > 0) {
			logger.info("합치거나 지운 스토리라인 {}개를 따라 사용자 {}명의 구독을 고쳤다", ids.size(), followed);
		}
		return followed;
	}

	/** 따라간 뒤의 구독 행과 바뀐 것이 있었는가 */
	private record Followed(Map<Long, Instant> rows, boolean changed) {
	}

}
