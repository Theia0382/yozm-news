package net.yozmnews.event;

/**
 * 사건 식별자 — {@code events.id} · docs/specs/event.md §3
 */
public record EventId(long value) {

	/**
	 * @throws IllegalArgumentException 1 보다 작을 때
	 */
	public EventId {
		if (value < 1) {
			throw new IllegalArgumentException("사건 ID 는 1 이상이다: " + value);
		}
	}

}
