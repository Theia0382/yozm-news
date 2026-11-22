package net.yozmnews.digest.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.event.EventId;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;

/**
 * {@code storyline} 의 꼭대기 조회를 한 번에 묻는 상한씩 나눠 부른다 — 관심도 재료와 후보 선별이 함께 쓴다. docs/specs/digest.md §4
 */
final class StorylineTopsQueries {

	private StorylineTopsQueries() {
	}

	/**
	 * 사건마다 꼭대기 — {@link StorylineDirectory#MAX_TOP_EVENTS} 씩 나눠 묻는다
	 * @return 사건 → 꼭대기(소속 점수 순). 소속이 없는 · 합쳐졌거나 없는 사건은 빠진다
	 */
	static Map<EventId, List<StorylineId>> ofEvents(StorylineDirectory storylines, Collection<EventId> events) {
		List<EventId> ids = events.stream().distinct().toList();
		Map<EventId, List<StorylineId>> tops = new HashMap<>();
		for (int i = 0; i < ids.size(); i += StorylineDirectory.MAX_TOP_EVENTS) {
			int end = Math.min(ids.size(), i + StorylineDirectory.MAX_TOP_EVENTS);
			tops.putAll(storylines.topsOf(ids.subList(i, end)));
		}
		return tops;
	}

	/**
	 * 스토리라인마다 꼭대기 — {@link StorylineDirectory#MAX_TOP_STORYLINES} 씩 나눠 묻는다
	 * @return 받은 스토리라인 → 꼭대기. 없는 스토리라인은 빠진다
	 */
	static Map<StorylineId, StorylineId> ofStorylines(StorylineDirectory storylines,
			Collection<StorylineId> storylineIds) {
		List<StorylineId> ids = storylineIds.stream().distinct().toList();
		Map<StorylineId, StorylineId> tops = new HashMap<>();
		for (int i = 0; i < ids.size(); i += StorylineDirectory.MAX_TOP_STORYLINES) {
			int end = Math.min(ids.size(), i + StorylineDirectory.MAX_TOP_STORYLINES);
			tops.putAll(storylines.topsOfStorylines(ids.subList(i, end)));
		}
		return tops;
	}

}
