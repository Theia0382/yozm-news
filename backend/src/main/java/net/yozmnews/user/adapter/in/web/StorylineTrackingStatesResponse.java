package net.yozmnews.user.adapter.in.web;

import java.util.List;
import java.util.Map;

import net.yozmnews.storyline.StorylineId;

/**
 * 흐름 추적 상태 — 받은 스토리라인마다 그 흐름을 추적 중인가. docs/specs/user.md §3
 *
 * @param states 받은 순서대로(같은 ID 는 처음 자리 한 번)
 */
record StorylineTrackingStatesResponse(List<State> states) {

	static StorylineTrackingStatesResponse of(Map<StorylineId, Boolean> states) {
		return new StorylineTrackingStatesResponse(states.entrySet()
			.stream()
			.map(entry -> new State(entry.getKey().value(), entry.getValue()))
			.toList());
	}

	/**
	 * @param storylineId 받은 스토리라인 ID
	 * @param tracked 그 스토리라인의 꼭대기를 추적 대상으로 가진 사용자 스토리라인 가운데 하나라도 추적 중인가 — 없는 스토리라인은 false
	 */
	record State(long storylineId, boolean tracked) {
	}

}
