package net.yozmnews.digest.domain.interest;

import java.util.Objects;

/**
 * 기준 시각 하나의 관심 — 주제 중분류 관심과 스토리라인(꼭대기) 관심. 같은 재료를 한 번 나눠 함께 낸다({@link InterestWindow#levels}).
 * docs/specs/digest.md §5
 *
 * @param topics 주제 중분류마다 관심
 * @param storylines 꼭대기마다 관심
 */
public record Interests(InterestLevels topics, StorylineLevels storylines) {

	/** 신호가 없다 */
	public static final Interests NONE = new Interests(InterestLevels.NONE, StorylineLevels.NONE);

	public Interests {
		Objects.requireNonNull(topics, "topics");
		Objects.requireNonNull(storylines, "storylines");
	}

}
