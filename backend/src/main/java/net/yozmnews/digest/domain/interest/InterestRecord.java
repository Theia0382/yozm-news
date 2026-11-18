package net.yozmnews.digest.domain.interest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.feedback.FeedbackChange;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.StorylineSubscriptionChange;
import net.yozmnews.user.TopicSubscriptionChange;

/**
 * 범위 하나에서 읽은 관심도 재료 — 기준 시각마다 {@link InterestWindow} 가 창으로 잘라 쓴다. 기간 판단은 없다. docs/specs/digest.md §5
 *
 * @param tagSet 키를 푼 활성 태그 체계 버전
 * @param exposures 노출 행 — 끝낸 시각 · 기사 순
 * @param subtopics 노출 기사 → 활성 중분류 — 평가가 없는 기사는 없다
 * @param feedback 좋아요 / 싫어요 이력 — 기록 순
 * @param topicChanges 주제 구독 이력 — 시각 · 행 순
 * @param storylineChanges 스토리라인 구독 이력 — 시각 · 행 순
 * @param storylineShares 주제를 모을 스토리라인 → 주제 몫({@link StorylineTopics#shares})
 */
public record InterestRecord(TagSetId tagSet, List<ExposureRow> exposures, Map<ArticleId, Set<String>> subtopics,
		List<FeedbackChange> feedback, List<TopicSubscriptionChange> topicChanges,
		List<StorylineSubscriptionChange> storylineChanges, Map<StorylineId, Map<String, Double>> storylineShares) {

	public InterestRecord {
		Objects.requireNonNull(tagSet, "tagSet");
		exposures = List.copyOf(Objects.requireNonNull(exposures, "exposures"));
		Map<ArticleId, Set<String>> subtopicsCopy = new HashMap<>();
		Objects.requireNonNull(subtopics, "subtopics")
			.forEach((article, keys) -> subtopicsCopy.put(article, Set.copyOf(keys)));
		subtopics = Map.copyOf(subtopicsCopy);
		feedback = List.copyOf(Objects.requireNonNull(feedback, "feedback"));
		topicChanges = List.copyOf(Objects.requireNonNull(topicChanges, "topicChanges"));
		storylineChanges = List.copyOf(Objects.requireNonNull(storylineChanges, "storylineChanges"));
		Map<StorylineId, Map<String, Double>> sharesCopy = new HashMap<>();
		Objects.requireNonNull(storylineShares, "storylineShares")
			.forEach((storyline, shares) -> sharesCopy.put(storyline, Map.copyOf(shares)));
		storylineShares = Map.copyOf(sharesCopy);
	}

}
