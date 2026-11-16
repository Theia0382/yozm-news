package net.yozmnews.storyline;

import java.time.Duration;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 잇기 기준의 값 — 시간 창 · 아래 기준 · 위 기준 · 확률 기준 · docs/specs/storyline.md §3 · storyline-R9 · storyline-R35.
 *
 * @param lower 아래 기준 — 띠 [아래, 위) 의 시작. 위 기준과 같으면 띠가 없다
 * @param upper 위 기준 — 이 이상이면 묻지 않고 붙인다
 * @param probability 확률 기준 — 띠가 생기기 전의 기준 이력에는 없다(null)
 */
public record StorylineLinkingCriteria(Duration window, double lower, double upper, @Nullable Double probability) {

	public StorylineLinkingCriteria {
		Objects.requireNonNull(window, "window");
	}

}
