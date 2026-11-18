package net.yozmnews.storyline;

import java.util.Objects;

import net.yozmnews.event.EventId;
import org.jspecify.annotations.Nullable;

/**
 * 정돈이나 조각 합치기가 합치거나 지운 스토리라인 하나 — {@link StorylinesRetired} 가 싣는다 · docs/specs/storyline.md §3 · storyline-R35.
 *
 * @param id 더 이상 자기를 가리키지 않는 스토리라인 — 합쳐졌으면 남는 스토리라인을 가리키고, 지웠으면 행이 없다
 * @param remainingEvent 풀기(사건 하나만 남아 지운 것)에서 남은 사건 — 풀기가 아니면(지우기 · 합치기) null. 지운 스토리라인은 받는 쪽이 읽을
 * 행이 없어, 무엇이 사라졌는지 가리킬 재료로 싣는다
 */
public record RetiredStoryline(StorylineId id, @Nullable EventId remainingEvent) {

	public RetiredStoryline {
		Objects.requireNonNull(id, "id");
	}

}
