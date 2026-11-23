package net.yozmnews.user.domain.summary;

import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

/**
 * 정리 호출 하나의 재료 — 글을 쓸 꼭대기마다 사건 줄, 지난 정리를 고치는 호출이면 지난 글. docs/specs/user.md §4
 * <p>
 * 꼭대기마다 넣을 사건이나 지난 글 가운데 하나는 있다 — 둘 다 없는 꼭대기는 재료에 넣지 않는다(그 꼭대기는 글 없이 남는다).
 *
 * @param tops 글을 쓸 꼭대기(스토리라인 ID) → 넣을 사건(첫 기사 시각 순) — 꼭대기 ID 순, 비지 않는다
 * @param previousWhole 흐름 전체의 지난 글 — 처음부터 정리하는 호출이면 null
 * @param previousTops 꼭대기의 지난 글 — 지난 글이 있는 꼭대기만. 처음부터 정리하는 호출이면 비어 있다
 * @param zone 사건 날짜를 적을 시간대
 */
public record SummaryInput(Map<Long, List<SummaryEvent>> tops, @Nullable SummaryText previousWhole,
		Map<Long, SummaryText> previousTops, ZoneId zone) {

	public SummaryInput {
		Objects.requireNonNull(tops, "tops");
		Objects.requireNonNull(zone, "zone");
		previousTops = Map.copyOf(Objects.requireNonNull(previousTops, "previousTops"));
		if (tops.isEmpty()) {
			throw new IllegalArgumentException("꼭대기가 없는 정리 재료다");
		}
		TreeMap<Long, List<SummaryEvent>> ordered = new TreeMap<>();
		tops.forEach((top, events) -> ordered.put(top, List.copyOf(events)));
		tops = Collections.unmodifiableMap(ordered);
		if (previousWhole == null && !previousTops.isEmpty()) {
			throw new IllegalArgumentException("흐름 전체의 지난 글 없이 꼭대기의 지난 글만 있다");
		}
		if (!tops.keySet().containsAll(previousTops.keySet())) {
			throw new IllegalArgumentException("재료에 없는 꼭대기의 지난 글이 있다");
		}
		if (previousTops.values().stream().anyMatch(SummaryText::none)) {
			throw new IllegalArgumentException("글 없음을 지난 글로 넘겼다");
		}
		for (Map.Entry<Long, List<SummaryEvent>> top : tops.entrySet()) {
			if (top.getValue().isEmpty() && !previousTops.containsKey(top.getKey())) {
				throw new IllegalArgumentException("넣을 사건도 지난 글도 없는 꼭대기다: " + top.getKey());
			}
		}
	}

	/** 처음부터 정리하는 호출의 재료 */
	public static SummaryInput fresh(Map<Long, List<SummaryEvent>> tops, ZoneId zone) {
		return new SummaryInput(tops, null, Map.of(), zone);
	}

	/** 꼭대기 ID 순의 꼭대기 */
	public List<Long> topIds() {
		return List.copyOf(this.tops.keySet());
	}

}
