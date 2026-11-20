package net.yozmnews.digest.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.exposure.ExposureRepository;
import net.yozmnews.digest.domain.interest.ExposureRow;
import net.yozmnews.digest.domain.interest.InterestFormula;
import net.yozmnews.digest.domain.interest.InterestRecord;
import net.yozmnews.digest.domain.interest.InterestSignals;
import net.yozmnews.digest.domain.interest.InterestWindow;
import net.yozmnews.digest.domain.interest.StorylineTopics;
import net.yozmnews.digest.domain.selection.EventFacts;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.feedback.EventFeedback;
import net.yozmnews.feedback.FeedbackChange;
import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.StorylineSubscriptionChange;
import net.yozmnews.user.StorylineSubscriptionDirectory;
import net.yozmnews.user.TopicSubscriptionChanges;
import net.yozmnews.user.TopicSubscriptionHistory;
import org.springframework.stereotype.Service;

/**
 * 관심도의 재료를 범위 하나에서 읽는다 — 노출 행(자기 섹션 사건 행과 몫) · 좋아요 / 싫어요 이력({@code feedback} — 행마다 지금 사건) · 주제
 * 구독 변경 · 스토리라인 구독 변경({@code user}) · 스토리라인 사건의 소속 기사({@code storyline} · {@code event}) · 노출 사건과 스토리라인
 * 사건의 주제(소속 기사의 평가 — {@code tag}). 노출 행의 사건은 {@code event} 로 지금 사건으로 풀고, 지운 사건의 행은 버린다. 기준 시각마다
 * 창으로 자르는 일은 {@link InterestWindow}, 행을 고르는 판단은 {@link InterestSignals}, 계산은 {@link InterestFormula} 가 한다.
 * docs/specs/digest.md §4 · §5
 * <p>
 * 트랜잭션 없이 조회를 차례로 부른다. 사용자를 인자로 받는다 — 화면 경로에서는 세션 사용자로만 부른다.
 */
@Service
class InterestMaterials {

	private final ExposureRepository exposures;

	private final EventFeedback feedback;

	private final ActiveEvaluations activeEvaluations;

	private final TopicSubscriptionHistory topicHistory;

	private final StorylineSubscriptionDirectory storylineSubscriptions;

	private final StorylineDirectory storylines;

	private final EventDirectory events;

	InterestMaterials(ExposureRepository exposures, EventFeedback feedback, ActiveEvaluations activeEvaluations,
			TopicSubscriptionHistory topicHistory, StorylineSubscriptionDirectory storylineSubscriptions,
			StorylineDirectory storylines, EventDirectory events) {
		this.exposures = Objects.requireNonNull(exposures, "exposures");
		this.feedback = Objects.requireNonNull(feedback, "feedback");
		this.activeEvaluations = Objects.requireNonNull(activeEvaluations, "activeEvaluations");
		this.topicHistory = Objects.requireNonNull(topicHistory, "topicHistory");
		this.storylineSubscriptions = Objects.requireNonNull(storylineSubscriptions, "storylineSubscriptions");
		this.storylines = Objects.requireNonNull(storylines, "storylines");
		this.events = Objects.requireNonNull(events, "events");
	}

	/**
	 * @param active 맞출 활성 태그 체계 버전
	 * @param from 범위 시작(포함) — 첫 기준 시각의 창 시작({@link InterestWindow#start})
	 * @param to 범위 끝(제외)
	 * @return 범위의 재료. 재료의 키를 푼 활성 버전이 {@code active} 와 다르면 빈 값 — 부르는 쪽이 처음부터 다시 한다
	 * @throws IllegalArgumentException {@code from} 이 {@code to} 보다 늦을 때
	 */
	Optional<InterestRecord> read(UserId user, TagSetId active, Instant from, Instant to) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(active, "active");
		List<ExposureRow> exposed = current(this.exposures.exposures(user, from, to));
		Optional<Map<EventId, Set<String>>> subtopics = subtopics(
				exposed.stream().map(ExposureRow::event).distinct().toList(), active);
		if (subtopics.isEmpty()) {
			return Optional.empty();
		}
		List<FeedbackChange> reactions = this.feedback.changes(user, from, to);
		TopicSubscriptionChanges topics = this.topicHistory.changes(user, from, to);
		if (!topics.tagSet().equals(active)) {
			return Optional.empty();
		}
		List<StorylineSubscriptionChange> storylineChanges = this.storylineSubscriptions.changes(user, from, to);
		Optional<Map<StorylineId, Map<String, Double>>> shares = shares(
				InterestSignals.storylinesToRead(storylineChanges), active);
		if (shares.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new InterestRecord(active, exposed, subtopics.get(), reactions, topics.changes(),
				storylineChanges, shares.get()));
	}

	/**
	 * 스토리라인마다 사건의 주제 몫 — 닫은 사건 집합의 사건마다 평가 있는 소속 기사로 사건의 주제({@link EventFacts})를 세어
	 * {@link StorylineTopics} 에 넘긴다. 평가 있는 소속 기사가 없는 사건은 빠진다. 버전이 어긋나면 빈 값
	 */
	private Optional<Map<StorylineId, Map<String, Double>>> shares(Set<StorylineId> ids, TagSetId active) {
		Map<StorylineId, Map<String, Double>> shares = new HashMap<>();
		for (StorylineId id : ids) {
			List<EventId> eventIds = this.storylines.events(id).stream().map(member -> member.event().id()).toList();
			Map<EventId, List<ArticleId>> members = eventIds.isEmpty() ? Map.of() : this.events.articleIdsOf(eventIds);
			Optional<Map<EventId, EventFacts>> facts = this.activeEvaluations.eventFacts(members, active);
			if (facts.isEmpty()) {
				return Optional.empty();
			}
			List<Set<String>> eventTopics = facts.get().values().stream().map(EventFacts::subtopics).toList();
			shares.put(id, StorylineTopics.shares(eventTopics));
		}
		return Optional.of(shares);
	}

	/**
	 * 노출 행의 사건을 지금 사건으로 푼다 — {@link EventDirectory#MAX_RESOLVE_EVENTS} 씩 나눠 묻고, 지운 사건의 행은 버린다. 순서 그대로
	 */
	private List<ExposureRow> current(List<ExposureRow> rows) {
		List<EventId> ids = rows.stream().map(ExposureRow::event).distinct().toList();
		Map<EventId, EventId> current = new HashMap<>();
		for (int i = 0; i < ids.size(); i += EventDirectory.MAX_RESOLVE_EVENTS) {
			int end = Math.min(ids.size(), i + EventDirectory.MAX_RESOLVE_EVENTS);
			current.putAll(this.events.currentOf(ids.subList(i, end)));
		}
		List<ExposureRow> resolved = new ArrayList<>(rows.size());
		for (ExposureRow row : rows) {
			EventId now = current.get(row.event());
			if (now != null) {
				resolved.add(row.withEvent(now));
			}
		}
		return resolved;
	}

	/**
	 * 노출한 (지금) 사건마다 사건의 주제 — 평가 있는 소속 기사 전부로 센다({@link ActiveEvaluations#eventFacts}). 평가 있는 소속 기사가 없는
	 * 사건은 빠진다. 버전이 어긋나면 빈 값
	 */
	private Optional<Map<EventId, Set<String>>> subtopics(List<EventId> events, TagSetId active) {
		Map<EventId, List<ArticleId>> members = events.isEmpty() ? Map.of() : this.events.articleIdsOf(events);
		Optional<Map<EventId, EventFacts>> facts = this.activeEvaluations.eventFacts(members, active);
		if (facts.isEmpty()) {
			return Optional.empty();
		}
		Map<EventId, Set<String>> subtopics = new HashMap<>();
		facts.get().forEach((event, ofEvent) -> subtopics.put(event, ofEvent.subtopics()));
		return Optional.of(subtopics);
	}

}
