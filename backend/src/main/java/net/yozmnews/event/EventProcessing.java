package net.yozmnews.event;

import java.util.List;

import net.yozmnews.platform.RoundQueue;

/**
 * 사건 쪽 회차의 처리 현황 — 회차마다 남은 항목 · 미룬 항목의 수와 회차 기록의 최근 합 · docs/specs/event.md §3.
 */
public interface EventProcessing {

	/**
	 * 회차 넷의 처리 현황 — 기사 표현({@code event-features}) · 사건 배정({@code event-assignment}) · 사건 재판정
	 * ({@code event-rejudgement}) · 사건 병합({@code event-merge}) 순. 기준 이력에 쓰지 않는다.
	 */
	List<RoundQueue> rounds();

}
