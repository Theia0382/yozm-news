package net.yozmnews.digest.domain.item;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.digest.domain.selection.SelectedEvent;
import net.yozmnews.event.EventId;
import net.yozmnews.storyline.StorylineId;
import org.jspecify.annotations.Nullable;

/**
 * 항목 하나 — 한 스토리라인으로 묶은 고른 사건들 · docs/specs/digest.md §5 · digest-R46
 *
 * @param events 항목의 사건 — 비지 않음, 같은 사건은 한 번
 * @param bigRow 큰 판정 나무의 항목이면 그 큰 판정 행의 위치 — 아니면 null
 * @param top 항목을 묶은 포함 나무의 꼭대기 — 큰 판정 나무의 항목이면 그 행 머리의 꼭대기. 꼭대기 없이 혼자 한 항목이면 null
 */
public record DigestItemPlan(List<SelectedEvent> events, @Nullable Integer bigRow, @Nullable StorylineId top) {

	/**
	 * @throws IllegalArgumentException 사건이 없거나 같은 사건이 두 번 있을 때
	 */
	public DigestItemPlan {
		events = List.copyOf(Objects.requireNonNull(events, "events"));
		if (events.isEmpty()) {
			throw new IllegalArgumentException("사건이 없는 항목이다");
		}
		Set<EventId> seen = new HashSet<>();
		for (SelectedEvent event : events) {
			if (!seen.add(event.event().facts().id())) {
				throw new IllegalArgumentException("같은 사건이 항목에 두 번 있다: " + event.event().facts().id().value());
			}
		}
	}

}
