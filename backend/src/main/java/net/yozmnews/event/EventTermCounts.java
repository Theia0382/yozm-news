package net.yozmnews.event;

import java.util.Map;

/**
 * 말 수 — 주어진 시각 범위와 겹치는 합쳐지지 않은 사건의 수와, 그 가운데 말마다 그 말을 핵심 말로 가진 사건 수 · docs/specs/event.md §3 ·
 * event-R24. 스토리라인 잇기가 말이 얼마나 흔한지 재는 재료다.
 *
 * @param events 범위와 겹치는 합쳐지지 않은 사건 수 — 0 이상
 * @param eventsByTerm 물은 말 → 그 말을 핵심 말로 가진 범위 안 사건 수 — 0 이상, 물은 말마다 하나. 복사해 받는다
 */
public record EventTermCounts(long events, Map<String, Long> eventsByTerm) {

	/**
	 * @throws IllegalArgumentException 수가 음수일 때
	 */
	public EventTermCounts {
		if (events < 0) {
			throw new IllegalArgumentException("사건 수는 0 이상이다: " + events);
		}
		eventsByTerm = Map.copyOf(eventsByTerm);
		eventsByTerm.forEach((term, count) -> {
			if (count < 0) {
				throw new IllegalArgumentException("말을 가진 사건 수는 0 이상이다: " + term + " " + count);
			}
		});
	}

}
