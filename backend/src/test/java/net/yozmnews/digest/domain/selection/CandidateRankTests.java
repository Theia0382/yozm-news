package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;

import net.yozmnews.digest.domain.interest.InterestWeights;
import net.yozmnews.digest.domain.interest.StorylineInterests;
import net.yozmnews.digest.domain.interest.TopicInterests;
import net.yozmnews.storyline.StorylineId;
import org.junit.jupiter.api.Test;

/**
 * 사건 점수 — 중요도 × {@code low_confidence} 가중치 × 주제 가중치(중분류 평균) × 스토리라인 가중치(꼭대기 평균). 기대값은 손으로 센 리터럴이다.
 * <p>
 * 지키는 규칙: digest-R18
 */
class CandidateRankTests {

	private static final StorylineId TREE = new StorylineId(100);

	private static final StorylineId OTHER = new StorylineId(200);

	@Test
	void theStorylineWeightMultipliesTheScore() {
		CandidateEvent event = Facts.withTops(Facts.single(Facts.article(1).importance(2.0)), TREE);
		TopicInterests topics = new TopicInterests(Map.of("politics.executive", 1.25));
		// 대조 — 스토리라인 가중치가 중립이면 2.0 × 1.25 = 2.5
		assertThat(CandidateRank.score(event, new InterestWeights(topics, StorylineInterests.NEUTRAL_ALL)).orElseThrow())
			.isCloseTo(2.5, within(1e-12));

		// 꼭대기 x 0.25 → g 1.125 — 2.0 × 1.0 × 1.25 × 1.125 = 2.8125
		InterestWeights weights = new InterestWeights(topics, new StorylineInterests(Map.of(TREE, 1.125)));

		assertThat(CandidateRank.score(event, weights).orElseThrow()).isCloseTo(2.8125, within(1e-12));
	}

	@Test
	void anEventOnTwoTreesAveragesTheirWeights() {
		// (1.5 + 0.5) ÷ 2 = 1 — 2.0 × 1 = 2.0, 꼭대기 없는 사건은 스토리라인 가중치가 중립
		InterestWeights weights = new InterestWeights(TopicInterests.NEUTRAL_ALL,
				new StorylineInterests(Map.of(TREE, 1.5, OTHER, 0.5)));

		assertThat(CandidateRank
			.score(Facts.withTops(Facts.single(Facts.article(1).importance(2.0)), TREE, OTHER), weights)
			.orElseThrow()).isCloseTo(2.0, within(1e-12));
		assertThat(CandidateRank.score(Facts.single(Facts.article(2).importance(2.0)), weights).orElseThrow())
			.isCloseTo(2.0, within(1e-12));
	}

}
