package net.yozmnews.storyline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.event.EventId;
import org.junit.jupiter.api.Test;

/**
 * 합치거나 지운 스토리라인 알림 — 비어 있지 않고 같은 스토리라인이 두 번 없으며 목록을 복사해 받는다. 남은 사건은 풀기에만 있다.
 * <p>
 * 지키는 규칙: storyline-R35
 */
class StorylinesRetiredTests {

	@Test
	void aNotificationHasDistinctStorylinesAndIsNotEmpty() {
		List<RetiredStoryline> storylines = new ArrayList<>(List.of(new RetiredStoryline(new StorylineId(2), null),
				new RetiredStoryline(new StorylineId(1), new EventId(7))));
		StorylinesRetired retired = new StorylinesRetired(storylines);
		storylines.add(new RetiredStoryline(new StorylineId(3), null));

		assertThat(retired.storylines()).containsExactly(new RetiredStoryline(new StorylineId(2), null),
				new RetiredStoryline(new StorylineId(1), new EventId(7)));
		assertThatIllegalArgumentException().isThrownBy(() -> new StorylinesRetired(List.of()));
		// 같은 스토리라인이면 남은 사건이 달라도 두 번이다
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new StorylinesRetired(List.of(new RetiredStoryline(new StorylineId(1), null),
					new RetiredStoryline(new StorylineId(1), new EventId(7)))));
	}

	@Test
	void aRetiredStorylineNeedsItsIdAndMayLackARemainingEvent() {
		assertThat(new RetiredStoryline(new StorylineId(1), null).remainingEvent()).isNull();
		assertThatNullPointerException().isThrownBy(() -> new RetiredStoryline(null, new EventId(7)));
	}

}
