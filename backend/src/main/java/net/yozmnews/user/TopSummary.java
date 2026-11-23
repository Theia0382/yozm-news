package net.yozmnews.user;

import java.util.Objects;

import net.yozmnews.storyline.StorylineId;
import org.jspecify.annotations.Nullable;

/**
 * 추적 대상 꼭대기 하나의 글. docs/specs/user.md §3
 * <p>
 * 글이 없는 꼭대기도 있다 — 정리할 사건이 없었거나, 마지막 정리 뒤에 추적 대상에 든 꼭대기다. 그때는 세 칸이 모두 null 이다.
 *
 * @param storyline 꼭대기의 스토리라인 ID
 * @param title 제목 — 글이 없으면 null
 * @param summary 흐름 요약 — 글이 없으면 null
 * @param recent 최근 전개 — 글이 없으면 null
 */
public record TopSummary(StorylineId storyline, @Nullable String title, @Nullable String summary,
		@Nullable String recent) {

	public TopSummary {
		Objects.requireNonNull(storyline, "storyline");
		if ((title == null) != (summary == null) || (title == null) != (recent == null)) {
			throw new IllegalArgumentException("꼭대기 글의 세 칸은 함께 있거나 함께 없다");
		}
	}

	/** 글이 없는 꼭대기 */
	public static TopSummary without(StorylineId storyline) {
		return new TopSummary(storyline, null, null, null);
	}

	/** 글이 있는가 */
	public boolean written() {
		return this.title != null;
	}

}
