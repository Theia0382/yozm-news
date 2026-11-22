package net.yozmnews.user.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineState;
import net.yozmnews.storyline.StorylineView;
import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.StorylineSubscriptionCause;
import net.yozmnews.user.UserStorylineId;
import net.yozmnews.user.domain.subscription.StorylineSubscriptionLimit;
import net.yozmnews.user.domain.subscription.StorylineSubscriptionRepository;
import net.yozmnews.user.domain.subscription.SubscriptionChange;
import net.yozmnews.user.domain.summary.StorylineSummary;
import net.yozmnews.user.domain.summary.StorylineSummaryRepository;
import net.yozmnews.user.domain.userstoryline.StoredUserStoryline;
import net.yozmnews.user.domain.userstoryline.UserStorylineRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 흐름 추적(스토리라인 구독)의 조회 · 추적 · 해제 · 추적 상태 — 세션 사용자의 것. docs/specs/user.md §4 · user-R52 · user-R53
 * <p>
 * 넷 모두 한 트랜잭션에서 설정 행을 잠그고(관심 설정과 같은 줄 — user-R46) 먼저 사용자 스토리라인을 맞춘 뒤 한다
 * ({@link UserStorylineFollowups#followLocked}). 추적은 사용자 스토리라인에 붙는다 — 받은 스토리라인의 포함 나무 꼭대기를 추적 대상으로
 * 가진 사용자 스토리라인이 추적 중이면 그 흐름의 어느 스토리라인에서 물어도 추적 중이다. 누른 스토리라인은 추적 씨앗으로 남고, 이력은 추적
 * 씨앗마다 한 행이다(원인은 사용자).
 */
@Service
public class StorylineSubscriptions {

	private final InterestSettingsLock lock;

	private final UserStorylineFollowups followups;

	private final UserStorylineMaterials materials;

	private final UserStorylineRepository userStorylines;

	private final StorylineSubscriptionRepository history;

	private final StorylineDirectory storylines;

	private final StorylineSummaryRepository summaries;

	StorylineSubscriptions(InterestSettingsLock lock, UserStorylineFollowups followups,
			UserStorylineMaterials materials, UserStorylineRepository userStorylines,
			StorylineSubscriptionRepository history, StorylineDirectory storylines,
			StorylineSummaryRepository summaries) {
		this.lock = Objects.requireNonNull(lock, "lock");
		this.followups = Objects.requireNonNull(followups, "followups");
		this.materials = Objects.requireNonNull(materials, "materials");
		this.userStorylines = Objects.requireNonNull(userStorylines, "userStorylines");
		this.history = Objects.requireNonNull(history, "history");
		this.storylines = Objects.requireNonNull(storylines, "storylines");
		this.summaries = Objects.requireNonNull(summaries, "summaries");
	}

	/**
	 * 내 추적 — 추적 중인 사용자 스토리라인마다 한 줄. 추적 시각 내림차순, 같으면 사용자 스토리라인 ID 순. 줄마다 지금 있는 추적 씨앗(ID 순)과
	 * 그 가운데 가장 이어지는 상태, 정리가 있으면 정리의 제목을 싣는다. 추적 씨앗이 하나도 남지 않은 줄은 내지 않는다
	 */
	@Transactional
	public SubscriptionsView view(UserId user, Instant now) {
		List<StoredUserStoryline> tracked = followed(user, now).stream().filter(StoredUserStoryline::tracked).toList();
		Map<StorylineId, StorylineView> views = this.storylines.findAll(tracked.stream()
			.flatMap(storyline -> storyline.trackedSeeds().keySet().stream())
			.map(StorylineId::new)
			.toList());
		Map<Long, String> titles = new HashMap<>();
		for (StorylineSummary summary : this.summaries.load(user)) {
			titles.put(summary.userStorylineId(), summary.whole().title());
		}
		List<SubscribedUserStoryline> subscribed = new ArrayList<>();
		for (StoredUserStoryline storyline : tracked) {
			List<StorylineId> seeds = storyline.trackedSeeds()
				.keySet()
				.stream()
				.sorted()
				.map(StorylineId::new)
				.filter(views::containsKey)
				.toList();
			if (seeds.isEmpty()) {
				continue;
			}
			subscribed.add(new SubscribedUserStoryline(new UserStorylineId(storyline.id()), storyline.subscribedAt(),
					seeds, liveliest(seeds.stream().map(seed -> views.get(seed).state()).toList()),
					titles.get(storyline.id())));
		}
		subscribed.sort(Comparator.comparing(SubscribedUserStoryline::subscribedAt)
			.reversed()
			.thenComparing(subscription -> subscription.id().value()));
		return new SubscriptionsView(subscribed, StorylineSubscriptionLimit.MAX);
	}

	/** 추적 씨앗들의 상태 가운데 가장 이어지는 것 — 하나라도 진행이면 진행, 아니고 하나라도 잠잠이면 잠잠, 모두 끝났을 때만 끝남 */
	static StorylineState liveliest(List<StorylineState> states) {
		StorylineState liveliest = StorylineState.ENDED;
		for (StorylineState state : states) {
			liveliest = switch (state) {
				case ACTIVE -> StorylineState.ACTIVE;
				case QUIET -> (liveliest == StorylineState.ACTIVE) ? liveliest : StorylineState.QUIET;
				case ENDED -> liveliest;
			};
		}
		return liveliest;
	}

	/**
	 * 추적 — 받은 ID 가 합쳐졌으면 남는 스토리라인을 추적 씨앗으로 삼아 그 꼭대기의 사용자 스토리라인을 추적한다. 그 꼭대기를 추적 대상으로 가진
	 * 사용자 스토리라인이 없으면 만든다. 이미 추적 중인 사용자 스토리라인의 다른 스토리라인을 누르면 추적 씨앗만 더한다(상한을 보지 않는다 —
	 * 추적 중인 수가 늘지 않는다).
	 */
	@Transactional
	public SubscribeOutcome subscribe(UserId user, StorylineId storyline, Instant now) {
		List<StoredUserStoryline> current = followed(user, now);
		StorylineView found = this.storylines.findAll(List.of(storyline)).get(storyline);
		if (found == null) {
			return new SubscribeOutcome.UnknownStoryline();
		}
		long survivor = found.id().value();
		Long top = this.materials.topsOfStorylines(List.of(survivor)).get(survivor);
		if (top == null) {
			return new SubscribeOutcome.UnknownStoryline();
		}
		StoredUserStoryline holder = holderOf(top, current);
		if (holder != null && holder.tracked()) {
			if (holder.trackedSeeds().containsKey(survivor)) {
				return new SubscribeOutcome.AlreadySubscribed(found.id());
			}
			Set<Long> seeds = new TreeSet<>(holder.trackedSeeds().keySet());
			seeds.add(survivor);
			this.userStorylines.replaceSeeds(holder.id(), seeds);
		}
		else {
			if (current.stream().filter(StoredUserStoryline::tracked).count() >= StorylineSubscriptionLimit.MAX) {
				return new SubscribeOutcome.LimitReached(StorylineSubscriptionLimit.MAX);
			}
			long id;
			if (holder == null) {
				id = this.userStorylines.create(user, now);
				this.userStorylines.replaceMembers(id, Set.of(top));
			}
			else {
				id = holder.id();
			}
			this.userStorylines.subscribe(user, id, now);
			this.userStorylines.replaceSeeds(id, Set.of(survivor));
		}
		this.history.record(user, List.of(new SubscriptionChange(survivor, StorylineSubscriptionAction.ADD)),
				StorylineSubscriptionCause.USER, now);
		// 새 추적 씨앗으로 추적 대상(이어진 뒤 흐름)을 채우고 겹치는 사용자 스토리라인과 합친다
		this.followups.followLocked(user, Map.of(), now);
		return new SubscribeOutcome.Subscribed(found.id());
	}

	/**
	 * 해제 — 받은 스토리라인의 꼭대기를 추적 대상으로 가진 사용자 스토리라인의 추적을 끈다. 그 추적 씨앗을 모두 지우고 씨앗마다 이력을
	 * 남긴다. 사용자 스토리라인은 지우지 않는다 — 신호가 관심 창 안에 있는 동안 남아, 다시 추적하면 같은 사용자 스토리라인이다.
	 */
	@Transactional
	public UnsubscribeOutcome unsubscribe(UserId user, StorylineId storyline, Instant now) {
		List<StoredUserStoryline> current = followed(user, now);
		StorylineView found = this.storylines.findAll(List.of(storyline)).get(storyline);
		StorylineId target = (found == null) ? storyline : found.id();
		Long top = (found == null) ? null : this.materials.topsOfStorylines(List.of(target.value())).get(target.value());
		StoredUserStoryline holder = (top == null) ? null : holderOf(top, current);
		if (holder == null || !holder.tracked()) {
			return new UnsubscribeOutcome.NotSubscribed();
		}
		this.userStorylines.unsubscribe(holder.id());
		this.userStorylines.replaceSeeds(holder.id(), Set.of());
		this.history.record(user,
				new TreeSet<>(holder.trackedSeeds().keySet()).stream()
					.map(seed -> new SubscriptionChange(seed, StorylineSubscriptionAction.REMOVE))
					.toList(),
				StorylineSubscriptionCause.USER, now);
		return new UnsubscribeOutcome.Unsubscribed(target);
	}

	/**
	 * 추적 상태 — 받은 스토리라인마다 그 꼭대기를 추적 대상으로 가진 사용자 스토리라인이 추적 중인가. 없는 스토리라인은 추적 중이 아니다.
	 * @return 받은 순서대로 스토리라인 ID → 추적 중인가
	 */
	@Transactional
	public Map<StorylineId, Boolean> states(UserId user, Collection<StorylineId> storylineIds, Instant now) {
		List<StoredUserStoryline> current = followed(user, now);
		Map<Long, Long> tops = this.materials
			.topsOfStorylines(storylineIds.stream().map(StorylineId::value).toList());
		Map<StorylineId, Boolean> states = new LinkedHashMap<>();
		for (StorylineId id : storylineIds) {
			Long top = tops.get(id.value());
			StoredUserStoryline holder = (top == null) ? null : holderOf(top, current);
			states.put(id, holder != null && holder.tracked());
		}
		return states;
	}

	/** 설정 행을 만들고 · 잠그고 · 태그 선호와 사용자 스토리라인을 맞춘 뒤의 사용자 스토리라인 */
	private List<StoredUserStoryline> followed(UserId user, Instant now) {
		this.lock.lockFollowed(user, now);
		return this.followups.followLocked(user, Map.of(), now);
	}

	/** 그 꼭대기를 추적 대상으로 가진 사용자 스토리라인 — 한 꼭대기는 한 사용자 스토리라인에만 든다 */
	private static @Nullable StoredUserStoryline holderOf(long top, List<StoredUserStoryline> current) {
		return current.stream().filter(storyline -> storyline.tops().contains(top)).findFirst().orElse(null);
	}

	/**
	 * 내 추적.
	 *
	 * @param subscriptions 추적 시각 내림차순
	 * @param limit 추적 중인 사용자 스토리라인 수의 상한
	 */
	public record SubscriptionsView(List<SubscribedUserStoryline> subscriptions, int limit) {

		public SubscriptionsView {
			subscriptions = List.copyOf(subscriptions);
		}

	}

	/**
	 * 추적 중인 사용자 스토리라인 하나.
	 *
	 * @param id 사용자 스토리라인
	 * @param subscribedAt 추적하기 시작한 시각
	 * @param storylines 지금 있는 추적 씨앗(사용자가 누른, 또는 갈라질 때 물려받은 스토리라인) — ID 순, 하나 이상
	 * @param state 조회 시각의 상태 — 추적 씨앗 가운데 가장 이어지는 것. 끝나도 추적은 남는다
	 * @param title 정리의 제목 — 정리가 없으면 null
	 */
	public record SubscribedUserStoryline(UserStorylineId id, Instant subscribedAt, List<StorylineId> storylines,
			StorylineState state, @Nullable String title) {

		public SubscribedUserStoryline {
			storylines = List.copyOf(storylines);
		}

	}

	/** 추적의 결과 */
	public sealed interface SubscribeOutcome {

		/** 추적했다 — 남는 스토리라인 */
		record Subscribed(StorylineId id) implements SubscribeOutcome {
		}

		/** 이미 추적 씨앗이다 — 바꾸지 않았다 */
		record AlreadySubscribed(StorylineId id) implements SubscribeOutcome {
		}

		/** 없는 스토리라인이다(지웠거나 처음부터 없는 ID) */
		record UnknownStoryline() implements SubscribeOutcome {
		}

		/** 추적 중인 사용자 스토리라인 수가 상한이다 — 바꾸지 않았다 */
		record LimitReached(int limit) implements SubscribeOutcome {
		}

	}

	/** 해제의 결과 */
	public sealed interface UnsubscribeOutcome {

		/** 해제했다 */
		record Unsubscribed(StorylineId id) implements UnsubscribeOutcome {
		}

		/** 추적하지 않는 흐름이다 — 바꾸지 않았다 */
		record NotSubscribed() implements UnsubscribeOutcome {
		}

	}

}
