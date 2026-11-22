package net.yozmnews.digest.domain.interest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.FeedbackChange;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.StorylineSubscriptionChange;
import net.yozmnews.user.TopicSubscriptionChange;

/**
 * 범위 하나에서 읽은 관심도 재료 — 기준 시각마다 {@link InterestWindow} 가 창으로 잘라 쓴다. 기간 판단은 없다. docs/specs/digest.md §5 · digest-R25
 *
 * @param tagSet 키를 푼 활성 태그 체계 버전
 * @param exposures 노출 행 — 사건을 지금 사건으로 푼 것(지운 사건의 행은 없다), 끝낸 시각 · 다이제스트 · 사건 순
 * @param subtopics 노출한 지금 사건 → 활성 중분류(사건의 주제) — 평가 있는 소속 기사가 없는 사건은 없다
 * @param tops 노출한 지금 사건 → 포함 나무의 꼭대기({@code storyline} 의 꼭대기 조회 순서) — 소속이 없는 사건은 없다
 * @param feedback 좋아요 / 싫어요 이력 — 기록 순, 행마다 기록한 사건과 지금 사건
 * @param topicChanges 주제 구독 이력 — 시각 · 행 순
 * @param storylineChanges 스토리라인 구독 이력 — 시각 · 행 순
 * @param storylineTops 관심 신호로 셀 구독 행의 지금 스토리라인({@link InterestSignals#storylinesToRead}) → 그 꼭대기. 없는 스토리라인은 없다
 * @param flows 사용자 스토리라인 묶음 — 스토리라인 관심의 키. 범위 전체의 신호로 낸 것 하나를 모든 기준 시각이 함께 쓴다
 */
public record InterestRecord(TagSetId tagSet, List<ExposureRow> exposures, Map<EventId, Set<String>> subtopics,
		Map<EventId, List<StorylineId>> tops, List<FeedbackChange> feedback, List<TopicSubscriptionChange> topicChanges,
		List<StorylineSubscriptionChange> storylineChanges, Map<StorylineId, StorylineId> storylineTops,
		StorylineFlows flows) {

	public InterestRecord {
		Objects.requireNonNull(tagSet, "tagSet");
		exposures = List.copyOf(Objects.requireNonNull(exposures, "exposures"));
		Map<EventId, Set<String>> subtopicsCopy = new HashMap<>();
		Objects.requireNonNull(subtopics, "subtopics")
			.forEach((event, keys) -> subtopicsCopy.put(event, Set.copyOf(keys)));
		subtopics = Map.copyOf(subtopicsCopy);
		Map<EventId, List<StorylineId>> topsCopy = new HashMap<>();
		Objects.requireNonNull(tops, "tops").forEach((event, roots) -> topsCopy.put(event, List.copyOf(roots)));
		tops = Map.copyOf(topsCopy);
		feedback = List.copyOf(Objects.requireNonNull(feedback, "feedback"));
		topicChanges = List.copyOf(Objects.requireNonNull(topicChanges, "topicChanges"));
		storylineChanges = List.copyOf(Objects.requireNonNull(storylineChanges, "storylineChanges"));
		storylineTops = Map.copyOf(Objects.requireNonNull(storylineTops, "storylineTops"));
		Objects.requireNonNull(flows, "flows");
	}

}
