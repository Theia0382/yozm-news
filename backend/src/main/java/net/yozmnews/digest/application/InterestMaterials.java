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
import net.yozmnews.digest.domain.interest.ExposedArticle;
import net.yozmnews.digest.domain.interest.InterestFormula;
import net.yozmnews.digest.domain.interest.InterestRules;
import net.yozmnews.digest.domain.interest.InterestSignals;
import net.yozmnews.digest.domain.interest.StorylineTopics;
import net.yozmnews.digest.domain.interest.SubscriptionSignal;
import net.yozmnews.digest.domain.interest.TopicInterests;
import net.yozmnews.digest.domain.selection.ActiveEvaluation;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.feedback.ArticleFeedback;
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
 * 관심도의 재료를 읽어 산식에 넘긴다 — 노출(자기 인용 행) · 좋아요 / 싫어요 이력({@code feedback}) · 주제 구독 변경 · 스토리라인 구독 변경
 * ({@code user}) · 스토리라인 사건의 대표 기사({@code storyline} · {@code event}) · 노출 기사와 대표 기사의 평가({@code tag}). 행을 고르는
 * 판단은 {@link InterestSignals}, 계산은 {@link InterestFormula} 가 한다. docs/specs/digest.md §4 · §5
 * <p>
 * 읽는 범위는 [기준 시각 − {@link InterestRules#lookback()}, 지금)이다 — 기준 시각 뒤의 노출 · 신호(같은 기간을 다시 만들 때 앞 다이제스트의
 * 노출과 그 뒤 누른 반응)도 읽고 무게 1 로 센다. 트랜잭션 없이 조회를 차례로 부른다. 범위 시작 앞에 누른 반응은 읽지 않는다 — 범위 경계를 걸쳐 같은
 * 기사가 다시 인용된 경우에만 그 반응을 없음으로 센다. 백그라운드 작업 전용이다.
 */
@Service
class InterestMaterials {

	private final ExposureRepository exposures;

	private final ArticleFeedback feedback;

	private final ActiveEvaluations activeEvaluations;

	private final TopicSubscriptionHistory topicHistory;

	private final StorylineSubscriptionDirectory storylineSubscriptions;

	private final StorylineDirectory storylines;

	private final EventDirectory events;

	InterestMaterials(ExposureRepository exposures, ArticleFeedback feedback, ActiveEvaluations activeEvaluations,
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
	 * @param active 맞출 활성 태그 체계 버전 — 관심 설정의 키 버전
	 * @param reference 기준 시각 — 다이제스트 기간 끝
	 * @param now 부르는 시각 — 읽는 범위의 끝
	 * @return 주제 중분류마다 가중치. 재료의 키를 푼 활성 버전이 {@code active} 와 다르면 빈 값 — 부르는 쪽이 처음부터 다시 한다
	 */
	Optional<TopicInterests> read(UserId user, TagSetId active, Instant reference, Instant now) {
		Objects.requireNonNull(user, "user");
		Instant from = reference.minus(InterestRules.lookback());
		Instant to = now.isAfter(from) ? now : from;
		Map<ArticleId, Instant> exposed = this.exposures.latestExposures(user, from, to);
		Optional<Map<ArticleId, ActiveEvaluation>> evaluated = this.activeEvaluations.read(exposed.keySet(), active);
		if (evaluated.isEmpty()) {
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
		List<ExposedArticle> articles = InterestSignals.exposed(exposed, reactions, subtopics(evaluated.get()));
		List<SubscriptionSignal> signals = new ArrayList<>(InterestSignals.topics(topics.changes()));
		signals.addAll(InterestSignals.storylines(storylineChanges, shares.get()));
		return Optional.of(InterestFormula.interests(articles, signals, reference));
	}

	/** 스토리라인마다 사건 대표 기사의 주제 몫 — 버전이 어긋나면 빈 값 */
	private Optional<Map<StorylineId, Map<String, Double>>> shares(Set<StorylineId> ids, TagSetId active) {
		Map<StorylineId, Map<String, Double>> shares = new HashMap<>();
		for (StorylineId id : ids) {
			List<EventId> eventIds = this.storylines.events(id).stream().map(member -> member.event().id()).toList();
			Map<EventId, ArticleId> representatives = eventIds.isEmpty() ? Map.of()
					: this.events.representativesOfAll(eventIds);
			Optional<Map<ArticleId, ActiveEvaluation>> evaluated = this.activeEvaluations
				.read(representatives.values(), active);
			if (evaluated.isEmpty()) {
				return Optional.empty();
			}
			shares.put(id, StorylineTopics.shares(
					evaluated.get().values().stream().map(ActiveEvaluation::subtopics).toList()));
		}
		return Optional.of(shares);
	}

	private static Map<ArticleId, Set<String>> subtopics(Map<ArticleId, ActiveEvaluation> evaluated) {
		Map<ArticleId, Set<String>> subtopics = new HashMap<>();
		evaluated.forEach((article, evaluation) -> subtopics.put(article, evaluation.subtopics()));
		return subtopics;
	}

}
