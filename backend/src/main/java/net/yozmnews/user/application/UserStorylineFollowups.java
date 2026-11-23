package net.yozmnews.user.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.RetiredStoryline;
import net.yozmnews.storyline.StorylinesRetired;
import net.yozmnews.user.InterestSignalWindow;
import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.StorylineSubscriptionCause;
import net.yozmnews.user.application.UserStorylineMaterials.Matched;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import net.yozmnews.user.domain.notice.NoticeRepository;
import net.yozmnews.user.domain.notice.RemovedSubscription;
import net.yozmnews.user.domain.notice.StorylineSubscriptionsRemovedContent;
import net.yozmnews.user.domain.subscription.StorylineSubscriptionRepository;
import net.yozmnews.user.domain.subscription.SubscriptionChange;
import net.yozmnews.user.domain.subscription.SubscriptionFollow;
import net.yozmnews.user.domain.userstoryline.StoredUserStoryline;
import net.yozmnews.user.domain.userstoryline.UserStorylineReconcile;
import net.yozmnews.user.domain.userstoryline.UserStorylineReconcile.Created;
import net.yozmnews.user.domain.userstoryline.UserStorylineReconcile.Kept;
import net.yozmnews.user.domain.userstoryline.UserStorylineReconcile.Lost;
import net.yozmnews.user.domain.userstoryline.UserStorylineRepository;
import net.yozmnews.user.domain.userstoryline.UserStorylineRow;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사용자 스토리라인 따라가기 — 사용자의 사용자 스토리라인을 지금의 신호와 스토리라인 관계에 맞춰 고쳐 쓴다. docs/specs/user.md §4
 * <p>
 * 사용자 하나를 고쳐 쓰는 정본은 {@link #followLocked} 하나다 — 리스너({@link #followRetired})와 추적 조회 · 추적 · 해제 유스케이스가
 * 함께 쓴다. 판정은 {@link UserStorylineMaterials} 가 낸다(쓰지 않는 공개 조회와 같은 판정). 창은 지금 기준의 관심 신호 창이다
 * ({@link InterestSignalWindow}).
 * <p>
 * 이력: 합쳐져 옮긴 추적 씨앗은 옛 ID 의 뺌과(새로 더했으면) 남는 ID 의 더함({@code STORYLINE_MERGED}), 지워진 추적 씨앗은 뺌
 * ({@code STORYLINE_REMOVED}), 갈라져 물려받은 추적 씨앗은 더함({@code STORYLINE_SPLIT})으로 남긴다. 추적 중이던 사용자 스토리라인이
 * 이을 흐름 없이 사라지면 사라진 구독 알림 하나를 남긴다 — 알림은 스토리라인 ID 와 풀기의 남은 사건만 담는다.
 */
@Service
class UserStorylineFollowups {

	private static final Logger logger = LoggerFactory.getLogger(UserStorylineFollowups.class);

	/** 한 번에 읽는 사용자 수 */
	static final int BATCH = 100;

	private final UserStorylineMaterials materials;

	private final UserStorylineRepository userStorylines;

	private final StorylineSubscriptionRepository history;

	private final InterestSettingsRepository settings;

	private final NoticeRepository notices;

	private final InterestSignalWindow window;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	UserStorylineFollowups(UserStorylineMaterials materials, UserStorylineRepository userStorylines,
			StorylineSubscriptionRepository history, InterestSettingsRepository settings, NoticeRepository notices,
			InterestSignalWindow window, TransactionTemplate transaction, InstantSource clock) {
		this.materials = Objects.requireNonNull(materials, "materials");
		this.userStorylines = Objects.requireNonNull(userStorylines, "userStorylines");
		this.history = Objects.requireNonNull(history, "history");
		this.settings = Objects.requireNonNull(settings, "settings");
		this.notices = Objects.requireNonNull(notices, "notices");
		this.window = Objects.requireNonNull(window, "window");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * 설정 행을 잠근 사용자의 사용자 스토리라인을 맞춘다 — 부르는 쪽의 트랜잭션 안에서만. 바뀐 것이 없으면 아무것도 쓰지 않는다.
	 * @param remainingEvents 받은 이벤트가 실은 풀기의 남은 사건(스토리라인 ID → 사건 ID) — 이벤트 없이 부르면 빈 맵
	 * @return 맞춘 뒤의 사용자 스토리라인 — ID 순
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	List<StoredUserStoryline> followLocked(UserId user, Map<Long, Long> remainingEvents, Instant now) {
		return follow(user, remainingEvents, now).storylines();
	}

	private Followed follow(UserId user, Map<Long, Long> remainingEvents, Instant now) {
		Matched matched = this.materials.match(user, this.window.start(now), now);
		Map<Long, UserStorylineRow> rows = new HashMap<>();
		matched.rows().forEach(row -> rows.put(row.id(), row));
		UserStorylineReconcile result = matched.result();
		boolean changed = recordSeedFollow(user, matched.seedFollow(), now);
		List<StoredUserStoryline> next = new ArrayList<>();
		List<SubscriptionChange> inherited = new ArrayList<>();
		for (Kept kept : result.kept()) {
			UserStorylineRow row = rows.get(kept.id());
			changed |= write(user, row, kept.tops(), kept.trackedSeeds(), kept.subscribedAt());
			if (row.head() == null || row.head() != kept.head()) {
				this.userStorylines.setHead(kept.id(), kept.head());
				changed = true;
			}
			kept.inherited().forEach(seed -> inherited.add(new SubscriptionChange(seed, StorylineSubscriptionAction.ADD)));
			next.add(new StoredUserStoryline(kept.id(), kept.tops(), kept.trackedSeeds(), kept.subscribedAt(),
					row.createdAt(), kept.head()));
		}
		for (Created created : result.created()) {
			long id = this.userStorylines.create(user, now);
			this.userStorylines.replaceMembers(id, created.tops());
			this.userStorylines.setHead(id, created.head());
			if (created.subscribedAt() != null) {
				this.userStorylines.subscribe(user, id, created.subscribedAt());
				this.userStorylines.replaceSeeds(id, created.trackedSeeds().keySet());
			}
			created.inherited()
				.forEach(seed -> inherited.add(new SubscriptionChange(seed, StorylineSubscriptionAction.ADD)));
			next.add(new StoredUserStoryline(id, created.tops(), created.trackedSeeds(), created.subscribedAt(), now,
					created.head()));
			changed = true;
		}
		List<RemovedSubscription> vanished = new ArrayList<>();
		for (Lost lost : result.removed()) {
			if (lost.wasTracked() && lost.mergedInto() == null) {
				// 추적 중이던 것이 이을 흐름 없이 사라졌다 — 그 추적 씨앗의 스토리라인이 모두 지워졌다
				rows.get(lost.id())
					.seeds()
					.stream()
					.sorted()
					.forEach(seed -> vanished.add(new RemovedSubscription(seed, remainingEvents.get(seed))));
			}
			this.userStorylines.delete(lost.id());
			changed = true;
		}
		this.history.record(user, inherited, StorylineSubscriptionCause.STORYLINE_SPLIT, now);
		if (!vanished.isEmpty()) {
			this.notices.create(user, new StorylineSubscriptionsRemovedContent(vanished), now);
		}
		next.sort((a, b) -> Long.compare(a.id(), b.id()));
		return new Followed(next, changed);
	}

	/** 추적 씨앗의 합쳐짐 · 지워짐을 이력에 남긴다 — 씨앗 행 자체는 잇기 결과가 고쳐 쓴다 */
	private boolean recordSeedFollow(UserId user, SubscriptionFollow seedFollow, Instant now) {
		if (!seedFollow.changed()) {
			return false;
		}
		List<SubscriptionChange> merged = new ArrayList<>();
		for (SubscriptionFollow.Move move : seedFollow.moved()) {
			merged.add(new SubscriptionChange(move.from(), StorylineSubscriptionAction.REMOVE));
			if (move.added()) {
				merged.add(new SubscriptionChange(move.to(), StorylineSubscriptionAction.ADD));
			}
		}
		this.history.record(user, merged, StorylineSubscriptionCause.STORYLINE_MERGED, now);
		this.history.record(user,
				seedFollow.removed()
					.stream()
					.map(id -> new SubscriptionChange(id, StorylineSubscriptionAction.REMOVE))
					.toList(),
				StorylineSubscriptionCause.STORYLINE_REMOVED, now);
		return true;
	}

	/** 저장 행이 맞춘 값과 다른 칸만 고쳐 쓴다 */
	private boolean write(UserId user, UserStorylineRow row, Set<Long> tops, Map<Long, Long> trackedSeeds,
			@Nullable Instant subscribedAt) {
		boolean changed = false;
		if (!row.members().equals(tops)) {
			this.userStorylines.replaceMembers(row.id(), tops);
			changed = true;
		}
		if (!Objects.equals(row.subscribedAt(), subscribedAt)) {
			if (subscribedAt == null) {
				this.userStorylines.unsubscribe(row.id());
			}
			else {
				this.userStorylines.subscribe(user, row.id(), subscribedAt);
			}
			changed = true;
		}
		if (!row.seeds().equals(trackedSeeds.keySet())) {
			this.userStorylines.replaceSeeds(row.id(), trackedSeeds.keySet());
			changed = true;
		}
		return changed;
	}

	/**
	 * 합치거나 지운 스토리라인을 추적 대상이나 추적 씨앗으로 가진 사용자를 모두 따라가게 한다 — 트랜잭션 밖에서 부른다. 사용자마다 트랜잭션
	 * 하나라 한 사용자의 실패는 그 사용자만 되돌리고 예외는 그대로 올린다(이벤트 재처리가 남은 사용자를 고친다). 설정 행이 없는 사용자는
	 * 건너뛴다 — 사용자 스토리라인은 설정 행을 만든 뒤에만 저장된다.
	 * @return 사용자 스토리라인이 바뀐 사용자 수
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
			List<UserId> batch = this.userStorylines.usersHolding(ids, after, BATCH);
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
			logger.info("합치거나 지운 스토리라인 {}개를 따라 사용자 {}명의 사용자 스토리라인을 고쳤다", ids.size(), followed);
		}
		return followed;
	}

	/** 맞춘 뒤의 사용자 스토리라인과 바뀐 것이 있었는가 */
	private record Followed(List<StoredUserStoryline> storylines, boolean changed) {
	}

}
