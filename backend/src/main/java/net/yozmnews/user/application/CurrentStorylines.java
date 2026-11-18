package net.yozmnews.user.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import org.springframework.stereotype.Component;

/**
 * 기록된 스토리라인 ID 를 지금 가리키는 스토리라인 ID 로 푸는 정본 — 합쳐졌으면 남는 쪽, 지워졌으면 빠진다. 구독 따라가기 · 소비자 조회 ·
 * 내 구독 이력이 함께 쓴다. 풀기 자체는 {@code storyline} 의 {@link StorylineDirectory#findAll} 이 한다. docs/specs/user.md §4 ·
 * user-R53 · user-R55 · user-R63
 */
@Component
class CurrentStorylines {

	private final StorylineDirectory storylines;

	CurrentStorylines(StorylineDirectory storylines) {
		this.storylines = Objects.requireNonNull(storylines, "storylines");
	}

	/**
	 * 받은 ID 마다 지금 가리키는 스토리라인 ID — 같은 ID 는 한 번만 묻고, 받은 것이 없으면 묻지 않는다.
	 * @return 받은 ID → 지금 ID. 지워진 ID 는 들지 않는다
	 */
	Map<Long, Long> resolve(Collection<Long> ids) {
		Map<Long, Long> resolved = new HashMap<>();
		if (ids.isEmpty()) {
			return resolved;
		}
		this.storylines.findAll(ids.stream().distinct().map(StorylineId::new).toList())
			.forEach((requested, view) -> resolved.put(requested.value(), view.id().value()));
		return resolved;
	}

}
