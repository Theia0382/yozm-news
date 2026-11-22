package net.yozmnews.user.domain.userstoryline;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.yozmnews.user.domain.userstoryline.UserStorylineGroups.Group;
import org.jspecify.annotations.Nullable;

/**
 * 저장한 사용자 스토리라인을 지금의 묶음에 잇기 — 정본 · docs/specs/user.md §5 · user-R66.
 * <p>
 * 평가 순서:
 * <ol>
 * <li>저장 개체마다 이을 묶음을 고른다 — 그 개체의 추적 대상과 추적 씨앗의 꼭대기에 겹치는 꼭대기가 가장 많은 묶음, 같으면 가장 작은 꼭대기 ID 가
 * 작은 묶음. 겹치는 묶음이 없으면 고르지 않는다</li>
 * <li>묶음마다 그 묶음을 고른 개체 가운데 ID 가 가장 작은 것이 잇고, 같은 묶음을 고른 나머지는 사라진다(합쳐짐)</li>
 * <li>아무도 고르지 않은 묶음은 새 개체다</li>
 * <li>추적 씨앗은 그 꼭대기가 든 묶음으로 간다 — 추적 씨앗을 받은 묶음은 추적 중이고 추적 시각은 씨앗이 온 개체들의 것 가운데 가장 이른
 * 것이다</li>
 * <li>추적 중이던 개체의 추적 대상과 겹치는데 추적 씨앗을 받지 못한 묶음(갈라진 조각, 또는 추적 씨앗의 스토리라인이 모두 지워진 개체의 흐름)은
 * 추적을 물려받는다 — 그 묶음의 씨앗 꼭대기가 추적 씨앗이 되고 추적 시각은 그 개체들의 것 가운데 가장 이른 것이다</li>
 * <li>어느 묶음도 고르지 못한 저장 개체는 사라진다</li>
 * </ol>
 * 결과의 모든 개체에서 추적 중인 것과 추적 씨앗이 하나 이상인 것은 같다.
 *
 * @param kept 저장 개체가 이은 묶음 — 개체 ID 순
 * @param created 새 개체가 될 묶음 — 묶음 순서
 * @param removed 사라지는 저장 개체 — 개체 ID 순
 */
public record UserStorylineReconcile(List<Kept> kept, List<Created> created, List<Lost> removed) {

	public UserStorylineReconcile {
		kept = List.copyOf(Objects.requireNonNull(kept, "kept"));
		created = List.copyOf(Objects.requireNonNull(created, "created"));
		removed = List.copyOf(Objects.requireNonNull(removed, "removed"));
	}

	/**
	 * 저장 개체가 이은 묶음.
	 *
	 * @param id 사용자 스토리라인 ID
	 * @param tops 추적 대상 꼭대기
	 * @param trackedSeeds 추적 씨앗 스토리라인 → 그 꼭대기
	 * @param inherited 이번에 물려받아 추적 씨앗이 된 스토리라인 — {@code trackedSeeds} 의 키의 부분
	 * @param subscribedAt 추적을 시작한 시각 — 추적 중이 아니면 null
	 */
	public record Kept(long id, Set<Long> tops, Map<Long, Long> trackedSeeds, Set<Long> inherited,
			@Nullable Instant subscribedAt) {

		public Kept {
			tops = Set.copyOf(tops);
			trackedSeeds = Map.copyOf(trackedSeeds);
			inherited = Set.copyOf(inherited);
		}

	}

	/**
	 * 새 개체가 될 묶음 — 칸의 뜻은 {@link Kept} 와 같다.
	 */
	public record Created(Set<Long> tops, Map<Long, Long> trackedSeeds, Set<Long> inherited,
			@Nullable Instant subscribedAt) {

		public Created {
			tops = Set.copyOf(tops);
			trackedSeeds = Map.copyOf(trackedSeeds);
			inherited = Set.copyOf(inherited);
		}

	}

	/**
	 * 사라지는 저장 개체.
	 *
	 * @param id 사용자 스토리라인 ID
	 * @param wasTracked 추적 중이었는가
	 * @param mergedInto 합쳐져 사라지면 그 묶음을 이은 개체의 ID — 이을 묶음이 없어 사라지면 null
	 */
	public record Lost(long id, boolean wasTracked, @Nullable Long mergedInto) {
	}

	/**
	 * @param stored 그 사용자의 저장 개체
	 * @param groups 지금의 묶음 — 한 꼭대기는 한 묶음에만 든다
	 */
	public static UserStorylineReconcile decide(List<StoredUserStoryline> stored, List<Group> groups) {
		Objects.requireNonNull(stored, "stored");
		Objects.requireNonNull(groups, "groups");
		List<StoredUserStoryline> entities = stored.stream()
			.sorted(Comparator.comparingLong(StoredUserStoryline::id))
			.toList();
		Map<Long, Integer> groupOfTop = new HashMap<>();
		for (int index = 0; index < groups.size(); index++) {
			for (long top : groups.get(index).tops()) {
				groupOfTop.put(top, index);
			}
		}
		// ① 개체마다 이을 묶음
		Map<Long, Integer> chosen = new HashMap<>();
		for (StoredUserStoryline entity : entities) {
			Integer choice = choose(entity, groups, groupOfTop);
			if (choice != null) {
				chosen.put(entity.id(), choice);
			}
		}
		// ② 묶음마다 이을 개체 — ID 순으로 훑어 처음 고른 개체
		Map<Integer, Long> ownerOf = new HashMap<>();
		for (StoredUserStoryline entity : entities) {
			Integer choice = chosen.get(entity.id());
			if (choice != null) {
				ownerOf.putIfAbsent(choice, entity.id());
			}
		}
		// ④ 추적 씨앗은 그 꼭대기가 든 묶음으로
		Map<Integer, Map<Long, Long>> seedsOf = new HashMap<>();
		Map<Integer, Instant> sinceOf = new HashMap<>();
		for (StoredUserStoryline entity : entities) {
			entity.trackedSeeds().forEach((storyline, top) -> {
				Integer group = groupOfTop.get(top);
				if (group != null) {
					seedsOf.computeIfAbsent(group, ignored -> new TreeMap<>()).put(storyline, top);
					sinceOf.merge(group, Objects.requireNonNull(entity.subscribedAt()), UserStorylineReconcile::earlier);
				}
			});
		}
		// ⑤ 추적 씨앗을 받지 못한 묶음이 추적 중이던 개체의 추적 대상과 겹치면 물려받는다
		Map<Integer, Set<Long>> inheritedOf = new HashMap<>();
		for (int index = 0; index < groups.size(); index++) {
			if (seedsOf.containsKey(index)) {
				continue;
			}
			Group group = groups.get(index);
			for (StoredUserStoryline entity : entities) {
				if (entity.tracked() && overlaps(entity.tops(), group.tops())) {
					sinceOf.merge(index, Objects.requireNonNull(entity.subscribedAt()), UserStorylineReconcile::earlier);
				}
			}
			if (sinceOf.containsKey(index)) {
				Map<Long, Long> seeds = new TreeMap<>();
				group.seedTops().forEach(top -> seeds.put(top, top));
				seedsOf.put(index, seeds);
				inheritedOf.put(index, new TreeSet<>(group.seedTops()));
			}
		}
		List<Kept> kept = new ArrayList<>();
		List<Created> created = new ArrayList<>();
		for (int index = 0; index < groups.size(); index++) {
			Set<Long> tops = groups.get(index).tops();
			Map<Long, Long> seeds = seedsOf.getOrDefault(index, Map.of());
			Set<Long> inherited = inheritedOf.getOrDefault(index, Set.of());
			Instant since = sinceOf.get(index);
			Long owner = ownerOf.get(index);
			if (owner == null) {
				created.add(new Created(tops, seeds, inherited, since));
			}
			else {
				kept.add(new Kept(owner, tops, seeds, inherited, since));
			}
		}
		kept.sort(Comparator.comparingLong(Kept::id));
		// ②⑥ 잇지 못한 개체
		Set<Long> owners = new HashSet<>(ownerOf.values());
		List<Lost> removed = new ArrayList<>();
		for (StoredUserStoryline entity : entities) {
			if (!owners.contains(entity.id())) {
				Integer choice = chosen.get(entity.id());
				removed.add(new Lost(entity.id(), entity.tracked(), (choice == null) ? null : ownerOf.get(choice)));
			}
		}
		return new UserStorylineReconcile(kept, created, removed);
	}

	/** 겹치는 꼭대기가 가장 많은 묶음 — 같으면 앞 묶음(가장 작은 꼭대기 ID 가 작다). 겹치는 묶음이 없으면 null */
	private static @Nullable Integer choose(StoredUserStoryline entity, List<Group> groups,
			Map<Long, Integer> groupOfTop) {
		Set<Long> basis = new HashSet<>(entity.tops());
		basis.addAll(entity.trackedSeeds().values());
		Map<Integer, Integer> overlap = new TreeMap<>();
		for (long top : basis) {
			Integer group = groupOfTop.get(top);
			if (group != null) {
				overlap.merge(group, 1, Integer::sum);
			}
		}
		Integer best = null;
		for (Map.Entry<Integer, Integer> entry : overlap.entrySet()) {
			if (best == null || entry.getValue() > overlap.get(best)
					|| (entry.getValue().equals(overlap.get(best)) && order(groups, entry.getKey()) < order(groups, best))) {
				best = entry.getKey();
			}
		}
		return best;
	}

	private static long order(List<Group> groups, int index) {
		return new TreeSet<>(groups.get(index).tops()).first();
	}

	private static boolean overlaps(Set<Long> a, Set<Long> b) {
		return a.stream().anyMatch(b::contains);
	}

	private static Instant earlier(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

}
