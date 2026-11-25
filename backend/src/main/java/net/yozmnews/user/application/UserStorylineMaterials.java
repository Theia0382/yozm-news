package net.yozmnews.user.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.EventFeedback;
import net.yozmnews.feedback.FeedbackChange;
import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.StorylineSubscriptionCause;
import net.yozmnews.user.domain.subscription.StoredSubscriptionChange;
import net.yozmnews.user.domain.subscription.StorylineSubscriptionRepository;
import net.yozmnews.user.domain.subscription.SubscriptionFollow;
import net.yozmnews.user.domain.userstoryline.StoredUserStoryline;
import net.yozmnews.user.domain.userstoryline.UserStorylineGroups;
import net.yozmnews.user.domain.userstoryline.UserStorylineGroups.Group;
import net.yozmnews.user.domain.userstoryline.UserStorylineReconcile;
import net.yozmnews.user.domain.userstoryline.UserStorylineRepository;
import net.yozmnews.user.domain.userstoryline.UserStorylineRow;
import net.yozmnews.user.domain.userstoryline.UserStorylineSeeds;
import org.springframework.stereotype.Component;

/**
 * 사용자 스토리라인 맞추기의 재료와 판정 — 저장 행을 지금 스토리라인으로 풀고, 창 안의 신호와 지금 관계로 묶음을 내어
 * {@link UserStorylineReconcile} 로 잇는다. 저장하는 따라가기({@link UserStorylineFollowups})와 쓰지 않는 공개 조회
 * ({@link UserStorylineDirectoryService})가 이것 하나를 함께 쓴다. 쓰지 않는다. docs/specs/user.md §4
 * <p>
 * 재료: 저장 행({@link UserStorylineRepository}), 남는 스토리라인({@link CurrentStorylines}), 창 안의 반응 이력({@code feedback} 의
 * {@link EventFeedback#changes} — 지금 사건의 꼭대기), 창 안의 사용자 원인 추적 · 해제 이력, 꼭대기와 이어짐({@code storyline} 의
 * {@link StorylineDirectory}). 조회는 한 번에 묻는 상한씩 나눠 묻는다. 여러 조회는 한 스냅숏이 아니다.
 */
@Component
class UserStorylineMaterials {

	private final UserStorylineRepository userStorylines;

	private final StorylineSubscriptionRepository history;

	private final CurrentStorylines current;

	private final StorylineDirectory storylines;

	private final EventFeedback feedback;

	UserStorylineMaterials(UserStorylineRepository userStorylines, StorylineSubscriptionRepository history,
			CurrentStorylines current, StorylineDirectory storylines, EventFeedback feedback) {
		this.userStorylines = Objects.requireNonNull(userStorylines, "userStorylines");
		this.history = Objects.requireNonNull(history, "history");
		this.current = Objects.requireNonNull(current, "current");
		this.storylines = Objects.requireNonNull(storylines, "storylines");
		this.feedback = Objects.requireNonNull(feedback, "feedback");
	}

	/**
	 * 창 [{@code from}, {@code to}) 의 신호와 지금 관계로 맞춘 결과 — 저장 행은 읽기만 한다.
	 */
	Matched match(UserId user, Instant from, Instant to) {
		List<UserStorylineRow> rows = this.userStorylines.load(user);
		// 추적 씨앗을 남는 스토리라인으로 — 합쳐졌으면 남는 쪽, 지워졌으면 빠진다
		Map<Long, Instant> seedTimes = new TreeMap<>();
		for (UserStorylineRow row : rows) {
			if (row.subscribedAt() != null) {
				row.seeds().forEach(seed -> seedTimes.merge(seed, row.subscribedAt(), UserStorylineMaterials::earlier));
			}
		}
		Map<Long, Long> survivors = this.current.resolve(seedTimes.keySet());
		SubscriptionFollow seedFollow = SubscriptionFollow.decide(seedTimes, survivors);
		// 저장한 추적 대상과 남는 추적 씨앗의 지금 꼭대기
		Set<Long> toResolve = new HashSet<>(survivors.values());
		rows.forEach(row -> {
			toResolve.addAll(row.members());
			if (row.head() != null) {
				toResolve.add(row.head());
			}
		});
		Map<Long, Long> topOf = topsOfStorylines(toResolve);
		List<StoredUserStoryline> stored = new ArrayList<>();
		for (UserStorylineRow row : rows) {
			Set<Long> tops = new TreeSet<>();
			for (long member : row.members()) {
				Long top = topOf.get(member);
				if (top != null) {
					tops.add(top);
				}
			}
			Map<Long, Long> seeds = new TreeMap<>();
			if (row.subscribedAt() != null) {
				for (long seed : row.seeds()) {
					Long survivor = survivors.get(seed);
					Long top = (survivor == null) ? null : topOf.get(survivor);
					if (top != null) {
						seeds.put(survivor, top);
					}
				}
			}
			Long head = (row.head() == null) ? null : topOf.get(row.head());
			stored.add(new StoredUserStoryline(row.id(), tops, seeds, row.subscribedAt(), row.createdAt(), head));
		}
		Set<Long> trackedSeedTops = new HashSet<>();
		stored.forEach(entity -> trackedSeedTops.addAll(entity.trackedSeeds().values()));
		Set<Long> seedTops = UserStorylineSeeds.tops(reactedTops(user, from, to), changedTops(user, from, to),
				trackedSeedTops);
		List<Group> groups = UserStorylineGroups.of(seedTops, continuationsOf(seedTops));
		return new Matched(rows, stored, seedFollow, UserStorylineReconcile.decide(stored, groups));
	}

	/** 창 안에 반응 이력이 있는 사건(지금 사건 — 지운 사건은 빠진다)의 꼭대기 */
	private Set<Long> reactedTops(UserId user, Instant from, Instant to) {
		Set<EventId> events = new HashSet<>();
		for (FeedbackChange change : this.feedback.changes(user, from, to)) {
			if (change.current() != null) {
				events.add(change.current());
			}
		}
		Set<Long> tops = new HashSet<>();
		for (List<EventId> chunk : chunks(events, StorylineDirectory.MAX_TOP_EVENTS)) {
			this.storylines.topsOf(chunk)
				.values()
				.forEach(found -> found.forEach(top -> tops.add(top.value())));
		}
		return tops;
	}

	/** 창 안의 사용자 원인 추적 · 해제 이력의 스토리라인(남는 쪽)의 꼭대기 */
	private Set<Long> changedTops(UserId user, Instant from, Instant to) {
		Set<Long> recorded = new HashSet<>();
		for (StoredSubscriptionChange change : this.history.changes(user, from, to)) {
			if (change.cause() == StorylineSubscriptionCause.USER) {
				recorded.add(change.storylineId());
			}
		}
		return new HashSet<>(topsOfStorylines(recorded).values());
	}

	/** 받은 스토리라인 ID → 그 꼭대기 — 합쳐졌으면 남는 쪽의 꼭대기, 없는 스토리라인은 빠진다 */
	Map<Long, Long> topsOfStorylines(Collection<Long> storylineIds) {
		Map<Long, Long> tops = new HashMap<>();
		for (List<StorylineId> chunk : chunks(storylineIds.stream().map(StorylineId::new).toList(),
				StorylineDirectory.MAX_TOP_STORYLINES)) {
			this.storylines.topsOfStorylines(chunk)
				.forEach((storyline, top) -> tops.put(storyline.value(), top.value()));
		}
		return tops;
	}

	/**
	 * 씨앗과 씨앗에서 닿는 꼭대기 모두의 이어짐 — 헤드(뒤 스토리라인이 없는 꼭대기)를 가르려면 닿은 꼭대기의 이어짐도 알아야 한다
	 */
	private Map<Long, Set<Long>> continuationsOf(Set<Long> seedTops) {
		Map<Long, Set<Long>> continuations = reachOf(seedTops);
		Set<Long> reached = new HashSet<>();
		continuations.values().forEach(reached::addAll);
		reached.removeAll(continuations.keySet());
		continuations.putAll(reachOf(reached));
		return continuations;
	}

	private Map<Long, Set<Long>> reachOf(Set<Long> tops) {
		Map<Long, Set<Long>> continuations = new HashMap<>();
		for (List<StorylineId> chunk : chunks(tops.stream().map(StorylineId::new).toList(),
				StorylineDirectory.MAX_TOP_STORYLINES)) {
			this.storylines.continuationsOf(chunk).forEach((storyline, reached) -> {
				Set<Long> ids = new HashSet<>();
				reached.forEach(top -> ids.add(top.value()));
				continuations.put(storyline.value(), ids);
			});
		}
		return continuations;
	}

	private static <T> List<List<T>> chunks(Collection<T> values, int size) {
		List<T> distinct = values.stream().distinct().toList();
		List<List<T>> chunks = new ArrayList<>();
		for (int from = 0; from < distinct.size(); from += size) {
			chunks.add(distinct.subList(from, Math.min(distinct.size(), from + size)));
		}
		return chunks;
	}

	private static Instant earlier(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

	/**
	 * 맞춘 결과.
	 *
	 * @param rows 저장 행 그대로 — 저장한 때의 스토리라인 ID
	 * @param stored 저장 행을 지금 스토리라인 · 꼭대기로 푼 것 — {@code result} 를 낸 입력
	 * @param seedFollow 추적 씨앗의 따라가기 — 합쳐져 옮긴 씨앗과 지워진 씨앗
	 * @param result 잇기 결과
	 */
	record Matched(List<UserStorylineRow> rows, List<StoredUserStoryline> stored, SubscriptionFollow seedFollow,
			UserStorylineReconcile result) {
	}

}
