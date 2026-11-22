package net.yozmnews.user.domain.subscription;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 추적 씨앗 따라가기 판정 — 정본. docs/specs/user.md §5 · user-R53
 * <p>
 * 행은 사용자의 추적 씨앗(추적하려고 누른 스토리라인)이다. 행마다 지금 가리키는 스토리라인을 보고, 합쳐졌으면 남는 스토리라인으로 옮기고 사라졌으면
 * 뺀다. 같은 남는 스토리라인으로 가는 행이 여럿이거나 남는 스토리라인이 이미 행이면 행 하나만 두고, 추적 시각은 가장 이른 것이다. 행은 스토리라인
 * ID 순으로 보므로 결과는 입력 순서와 무관하다.
 *
 * @param next 따라간 뒤의 행 — 스토리라인 ID → 추적 시각
 * @param moved 합쳐져 옮긴 행 — 옮긴 행의 ID 순
 * @param removed 사라져 뺀 행 — ID 순
 */
public record SubscriptionFollow(Map<Long, Instant> next, List<Move> moved, List<Long> removed) {

	public SubscriptionFollow {
		next = Map.copyOf(next);
		moved = List.copyOf(moved);
		removed = List.copyOf(removed);
	}

	/**
	 * @param current 사용자의 지금 추적 씨앗 — 스토리라인 ID → 그 씨앗이 든 사용자 스토리라인의 추적 시각
	 * @param resolved 행의 ID 마다 지금 가리키는 스토리라인 ID(합쳐졌으면 남는 쪽) — 없는 ID 는 사라졌다
	 */
	public static SubscriptionFollow decide(Map<Long, Instant> current, Map<Long, Long> resolved) {
		Objects.requireNonNull(current, "current");
		Objects.requireNonNull(resolved, "resolved");
		TreeMap<Long, Instant> rows = new TreeMap<>(current);
		TreeMap<Long, Instant> next = new TreeMap<>();
		// 자기를 가리키는 행을 먼저 둔다 — 남는 스토리라인이 이미 행이면 옮겨 오는 행은 새로 더하지 않는다
		rows.forEach((id, at) -> {
			if (id.equals(resolved.get(id))) {
				next.put(id, at);
			}
		});
		List<Move> moved = new ArrayList<>();
		List<Long> removed = new ArrayList<>();
		rows.forEach((id, at) -> {
			Long to = resolved.get(id);
			if (to == null) {
				removed.add(id);
			}
			else if (!to.equals(id)) {
				Instant kept = next.get(to);
				moved.add(new Move(id, to, kept == null));
				next.put(to, (kept == null || at.isBefore(kept)) ? at : kept);
			}
		});
		return new SubscriptionFollow(next, moved, removed);
	}

	/** 바뀐 것이 있는가 — 없으면 쓸 것도 이력도 없다 */
	public boolean changed() {
		return !this.moved.isEmpty() || !this.removed.isEmpty();
	}

	/**
	 * 합쳐져 옮긴 행 하나.
	 *
	 * @param from 옮긴 행의 스토리라인(합쳐진 것)
	 * @param to 남는 스토리라인
	 * @param added to 를 새로 더했는가 — 이미 행이었거나 앞선 행이 먼저 to 로 옮겼으면 false
	 */
	public record Move(long from, long to, boolean added) {
	}

}
