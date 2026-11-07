package net.yozmnews.event.domain.event;

/** 사건의 상태 — 열린 사건에만 기사를 붙인다 · docs/specs/event.md §4 · event-R12 · event-R13 */
public enum EventStatus {

	/** 기사를 붙일 수 있다 */
	OPEN,

	/** 닫혔다 — 기사를 붙이지 않고, 배정을 재판정하지 않는다 */
	CLOSED,

	/** 다른 사건에 합쳐졌다 — 기사는 남는 사건으로 옮겼고, 이 행은 남는 사건을 가리킨다 */
	MERGED

}
