package net.yozmnews.digest.domain.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.digest.domain.selection.CandidateEvent;
import net.yozmnews.digest.domain.selection.SelectedEvent;
import net.yozmnews.event.EventId;
import net.yozmnews.storyline.StorylineId;
import org.jspecify.annotations.Nullable;

/**
 * 고른 사건을 항목으로 묶기 — 정본 · docs/specs/digest.md §5 · digest-R46
 * <p>
 * ① 큰 슬롯 사건은 그 큰 판정 행의 항목에 든다. ② 그 밖의 사건은 꼭대기가 없으면 혼자 한 항목이다. ③ 꼭대기가 하나면 그 꼭대기의 항목에 든다.
 * ④ 여럿이면 큰 판정 나무의 꼭대기를 먼저 → 고른 사건(큰 슬롯 사건 포함) 가운데 그 꼭대기를 가진 사건이 많은 꼭대기 → 꼭대기 조회의 순서(소속
 * 점수 순)로 하나를 고른다. 고른 꼭대기가 큰 판정 나무면 그 행의 항목에 든다 — 큰 판정 나무의 항목은 그 행의 위치를 갖는다. 이어짐은 보지
 * 않는다.
 * <p>
 * 큰 판정 나무는 자르기 뒤 큰 슬롯 사건이 하나 이상 남은 큰 판정 행의 나무다({@code bigHeads}). 고르지 않은 큰 판정 행의 나무는 큰 판정 나무가
 * 아니라 그 나무에 든 구독 사건은 일반 항목이 된다.
 */
public final class ItemGrouping {

	private ItemGrouping() {
	}

	/**
	 * @param events 고른 사건 — 같은 사건은 한 번. 사건의 꼭대기는 후보 사건이 싣는다({@link CandidateEvent#tops()} — 소속 점수 순)
	 * @param bigHeads 자르기 뒤 큰 슬롯 사건이 남은 큰 판정 행의 위치 → 그 행의 머리(그 나무의 꼭대기)
	 * @return 항목 — 첫 사건이 나온 순서, 항목 안 사건은 입력 순서
	 * @throws IllegalArgumentException 큰 슬롯 사건의 행이 {@code bigHeads} 에 없을 때
	 */
	public static List<DigestItemPlan> group(List<SelectedEvent> events, Map<Integer, StorylineId> bigHeads) {
		Objects.requireNonNull(events, "events");
		Objects.requireNonNull(bigHeads, "bigHeads");
		Map<StorylineId, Integer> bigRowOf = new HashMap<>();
		bigHeads.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> bigRowOf.putIfAbsent(entry.getValue(), entry.getKey()));
		Map<StorylineId, Integer> holders = new HashMap<>();
		for (SelectedEvent event : events) {
			topsOf(event).forEach(top -> holders.merge(top, 1, Integer::sum));
		}
		Map<Object, Group> groups = new LinkedHashMap<>();
		for (SelectedEvent event : events) {
			Integer row = event.bigRow();
			if (row != null) {
				if (!bigHeads.containsKey(row)) {
					throw new IllegalArgumentException("큰 슬롯 사건의 큰 판정 행 " + row + " 의 머리가 없다");
				}
				groups.computeIfAbsent(new RowKey(row), key -> new Group(row)).events.add(event);
				continue;
			}
			List<StorylineId> ofEvent = topsOf(event);
			if (ofEvent.isEmpty()) {
				groups.put(new SoloKey(event.event().facts().id()), new Group(null, event));
				continue;
			}
			StorylineId top = pick(ofEvent, bigRowOf, holders);
			Integer bigRow = bigRowOf.get(top);
			Object key = (bigRow != null) ? new RowKey(bigRow) : new TreeKey(top);
			groups.computeIfAbsent(key, ignored -> new Group(bigRow)).events.add(event);
		}
		return groups.values().stream().map(group -> new DigestItemPlan(group.events, group.bigRow)).toList();
	}

	/** 꼭대기 여럿 가운데 하나 — 큰 판정 나무 → 고른 사건이 많은 꼭대기 → 꼭대기 조회의 순서 */
	private static StorylineId pick(List<StorylineId> ofEvent, Map<StorylineId, Integer> bigRowOf,
			Map<StorylineId, Integer> holders) {
		List<StorylineId> ordered = new ArrayList<>(ofEvent);
		Comparator<StorylineId> order = Comparator.comparing((StorylineId top) -> !bigRowOf.containsKey(top))
			.thenComparing(top -> holders.getOrDefault(top, 0), Comparator.reverseOrder())
			.thenComparingInt(ofEvent::indexOf);
		ordered.sort(order);
		return ordered.getFirst();
	}

	private static List<StorylineId> topsOf(SelectedEvent event) {
		return event.event().tops();
	}

	private record RowKey(int row) {
	}

	private record TreeKey(StorylineId top) {
	}

	private record SoloKey(EventId event) {
	}

	/** 모으는 중인 항목 하나 */
	private static final class Group {

		private final @Nullable Integer bigRow;

		private final List<SelectedEvent> events = new ArrayList<>();

		Group(@Nullable Integer bigRow) {
			this.bigRow = bigRow;
		}

		Group(@Nullable Integer bigRow, SelectedEvent first) {
			this(bigRow);
			this.events.add(first);
		}

	}

}
