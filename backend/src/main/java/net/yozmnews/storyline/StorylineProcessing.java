package net.yozmnews.storyline;

import java.util.List;

import net.yozmnews.platform.RoundQueue;

/**
 * 흐름 쪽 회차의 처리 현황 — 회차마다 남은 항목 · 미룬 항목의 수와 회차 기록의 최근 합 · docs/specs/storyline.md §3.
 */
public interface StorylineProcessing {

	/** 회차 둘의 처리 현황 — 잇기({@code storyline-linking}) · 관계({@code storyline-relation}) 순 */
	List<RoundQueue> rounds();

}
