package net.yozmnews.storyline;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 스토리라인이 더 이상 자기를 가리키지 않는다 — 정돈이 합치거나(남는 스토리라인을 가리킨다) 지웠다(풀기 포함) · docs/specs/storyline.md
 * §3 · storyline-R35.
 * <p>
 * 무엇이 일어났는지는 싣지 않는다 — 받는 쪽이 {@link StorylineDirectory#findAll} 로 지금 상태를 읽어 남는 쪽을 찾고, 없으면 지워졌다.
 * 그래서 이벤트를 받는 순서가 바뀌어도 받는 쪽은 같은 상태로 수렴한다. 정돈 트랜잭션에서 한 번 발행한다.
 *
 * @param storylines 합치거나 지운 스토리라인 — 비어 있지 않고 같은 스토리라인이 두 번 없다
 */
public record StorylinesRetired(List<RetiredStoryline> storylines) {

	/**
	 * @throws IllegalArgumentException 목록이 비었거나 같은 스토리라인이 두 번 있을 때
	 */
	public StorylinesRetired {
		storylines = List.copyOf(storylines);
		if (storylines.isEmpty()) {
			throw new IllegalArgumentException("합치거나 지운 스토리라인이 없다");
		}
		Set<StorylineId> seen = new HashSet<>();
		for (RetiredStoryline storyline : storylines) {
			if (!seen.add(storyline.id())) {
				throw new IllegalArgumentException("같은 스토리라인이 두 번 있다: " + storyline.id().value());
			}
		}
	}

}
