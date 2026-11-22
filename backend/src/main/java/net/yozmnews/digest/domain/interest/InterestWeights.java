package net.yozmnews.digest.domain.interest;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.storyline.StorylineId;

/**
 * 후보 사건의 관심도 가중치 — 주제 가중치와 스토리라인(꼭대기) 가중치의 곱. 사건 점수 · 선정 이유 {@code INTEREST} 가 쓴다.
 * docs/specs/digest.md §5
 *
 * @param topics 주제 중분류마다 가중치
 * @param storylines 꼭대기마다 가중치
 */
public record InterestWeights(TopicInterests topics, StorylineInterests storylines) {

	/** 모든 주제 · 꼭대기가 중립 — 관심도를 보지 않는 순위 */
	public static final InterestWeights NEUTRAL_ALL = new InterestWeights(TopicInterests.NEUTRAL_ALL,
			StorylineInterests.NEUTRAL_ALL);

	public InterestWeights {
		Objects.requireNonNull(topics, "topics");
		Objects.requireNonNull(storylines, "storylines");
	}

	/** 기준 시각 하나의 관심을 가중치로 옮긴 것 */
	public static InterestWeights of(Interests interests) {
		Objects.requireNonNull(interests, "interests");
		return new InterestWeights(interests.topics().weights(), interests.storylines().weights());
	}

	/**
	 * 사건의 가중치 — 주제 가중치({@link TopicInterests#weightOf} — 주제 중분류 평균) × 스토리라인 가중치({@link StorylineInterests#weightOf}
	 * — 꼭대기 평균). 주제 · 꼭대기가 없으면 그쪽은 중립
	 */
	public double weightOf(Set<String> subtopics, List<StorylineId> tops) {
		return this.topics.weightOf(subtopics) * this.storylines.weightOf(tops);
	}

}
