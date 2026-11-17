package net.yozmnews.digest.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.digest.DigestPeriod;
import net.yozmnews.digest.domain.selection.ActiveEvaluation;
import net.yozmnews.digest.domain.selection.ArticleFacts;
import net.yozmnews.digest.domain.selection.BigSlotPicker;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigEvent;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigRow;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigSlots;
import net.yozmnews.digest.domain.selection.CandidateCost;
import net.yozmnews.digest.domain.selection.CandidateSelector;
import net.yozmnews.digest.domain.selection.EventDedup;
import net.yozmnews.digest.domain.selection.SelectionRules;
import net.yozmnews.digest.domain.selection.Shortlist;
import net.yozmnews.digest.domain.selection.SubscriptionFilter;
import net.yozmnews.digest.domain.selection.UserFilter;
import net.yozmnews.event.EventArticleView;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.event.EventTally;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.platform.UserId;
import net.yozmnews.source.SourceId;
import net.yozmnews.storyline.BigStoryline;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.ArticleEvaluations;
import net.yozmnews.tag.EvaluationPeriod;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.DigestInterests;
import net.yozmnews.user.DigestInterestsDirectory;
import org.springframework.stereotype.Service;

/**
 * 다이제스트 후보 선별 — 다른 모듈의 공개 조회로 재료를 읽어 {@code domain/selection} 의 순수 단계에 넘긴다 — docs/specs/digest.md §4
 * <p>
 * 트랜잭션 없이 조회를 차례로 부른다 — 한 스냅숏이 아니다. 그 사이 지운 기사는 재료에서 빠지고, 생성이 후보 행으로 다시 거른다(digest-R9).
 * 관심 설정의 키 버전과 기사 평가 키를 푼 계보 표의 활성 버전이 다르면(조회 사이의 태그 체계 활성 전환) 처음부터 한 번 더 하고, 그래도 다르면
 * {@link IllegalStateException} 이다. 계정이 없으면 빈 목록이다. 백그라운드 작업 전용이다 — 화면 경로에서 부르지 않는다.
 * <p>
 * 큰 슬롯은 큰 판정 목록의 행을 그대로 쓴다. 포함의 상위와 하위가 함께 크면 목록이 둘을 각각 행으로 낼 수 있는데, 앞 행이 쓴 사건은 뒤 행에서
 * 다시 들지 않는다 — 대신 한 포함 나무가 슬롯 둘을 쓸 수 있다.
 */
@Service
public class CandidateSelection {

	/** 활성 버전이 어긋날 때 처음부터 다시 하는 횟수를 포함한 전체 시도 수 */
	static final int ATTEMPTS = 2;

	private final DigestInterestsDirectory interests;

	private final ArticleEvaluations evaluations;

	private final ActiveEvaluations activeEvaluations;

	private final ArticleDirectory articles;

	private final EventDirectory events;

	private final StorylineDirectory storylines;

	CandidateSelection(DigestInterestsDirectory interests, ArticleEvaluations evaluations,
			ActiveEvaluations activeEvaluations, ArticleDirectory articles, EventDirectory events,
			StorylineDirectory storylines) {
		this.interests = Objects.requireNonNull(interests, "interests");
		this.evaluations = Objects.requireNonNull(evaluations, "evaluations");
		this.activeEvaluations = Objects.requireNonNull(activeEvaluations, "activeEvaluations");
		this.articles = Objects.requireNonNull(articles, "articles");
		this.events = Objects.requireNonNull(events, "events");
		this.storylines = Objects.requireNonNull(storylines, "storylines");
	}

	/**
	 * @param period 다이제스트 기간 — 시각 범위. 사용자 시간대의 날로 만드는 일은 부르는 쪽이 한다
	 * @param model 쓸 모델 — 입력 토큰 예산
	 * @return 후보 — 큰 슬롯 기사(선정 이유 {@code MAJOR_STORYLINE}) 다음 구독 후보({@code SUBSCRIBED})
	 * @throws IllegalStateException 관심 설정과 기사 평가 키의 활성 버전이 {@link #ATTEMPTS} 번 모두 어긋났을 때
	 */
	public List<DigestCandidate> select(UserId user, DigestPeriod period, LlmModel model) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(period, "period");
		Objects.requireNonNull(model, "model");
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			Optional<List<DigestCandidate>> selected = attempt(user, period, model);
			if (selected.isPresent()) {
				return selected.get();
			}
		}
		throw new IllegalStateException("관심 설정과 기사 평가 키의 태그 체계 활성 버전이 " + ATTEMPTS + " 번 어긋났다");
	}

	/** 한 번의 선별 — 활성 버전이 어긋나면 빈 값 */
	private Optional<List<DigestCandidate>> attempt(UserId user, DigestPeriod period, LlmModel model) {
		Optional<DigestInterests> found = this.interests.interestsOf(user);
		if (found.isEmpty()) {
			return Optional.of(List.of());
		}
		DigestInterests interests = found.get();
		Instant from = latest(period.start(), period.end().minus(EvaluationPeriod.MAX_LENGTH));
		EvaluationPeriod readPeriod = EvaluationPeriod.of(from, period.end()).orElseThrow();
		Optional<List<ArticleFacts>> facts = facts(this.evaluations.firstEvaluatedIn(readPeriod), interests.tagSet());
		if (facts.isEmpty()) {
			return Optional.empty();
		}
		UserFilter filter = UserFilter.of(interests);
		List<ArticleFacts> ranked = SubscriptionFilter.select(facts.get(), filter, from);
		Map<ArticleId, EventId> eventOf = ranked.isEmpty() ? Map.of()
				: this.events.eventsOf(ranked.stream().map(ArticleFacts::id).toList());
		Map<EventId, List<ArticleId>> groups = EventDedup.groups(ranked, eventOf);
		Map<EventId, ArticleId> representatives = groups.isEmpty() ? Map.of() : this.events.representatives(groups);
		Optional<BigSlots> big = bigSlots(interests, period, filter);
		if (big.isEmpty()) {
			return Optional.empty();
		}
		Shortlist shortlist = CandidateSelector.shortlist(ranked, eventOf, representatives, big.get(), filter,
				interests.maxCandidates());
		int budget = CandidateCost.candidateBudget(model.inputTokenBudget(), interests.interestTextLength(),
				interests.topics().size(), interests.excludedRegions().size());
		return Optional.of(CandidateSelector.candidates(shortlist, tokens(shortlist), budget));
	}

	/**
	 * 큰 슬롯 — 기간의 큰 판정 목록(보도 강도 순)의 행마다 기간 안 기사가 있는 사건과 그 중요도 합, 사건마다 기간 안 기사 가운데 평가가 있고
	 * 사용자의 제외를 지나는 기사의 대표를 읽어 {@link BigSlotPicker} 에 넘긴다. 조회 기간은 끝에서 {@link EventPeriod#MAX_LENGTH} 까지로
	 * 줄인다. 큰 슬롯 수가 0 이면 읽지 않는다.
	 * @return 계보 표의 활성 버전이 관심 설정의 버전과 다르면 빈 값
	 */
	private Optional<BigSlots> bigSlots(DigestInterests interests, DigestPeriod period, UserFilter filter) {
		if (interests.bigSlots() <= 0) {
			return Optional.of(BigSlots.NONE);
		}
		Instant from = latest(period.start(), period.end().minus(EventPeriod.MAX_LENGTH));
		EventPeriod read = EventPeriod.of(from, period.end()).orElseThrow();
		List<BigStoryline> big = this.storylines.bigStorylines(read);
		if (big.isEmpty()) {
			return Optional.of(BigSlots.NONE);
		}
		Map<StorylineId, List<EventId>> rowEvents = new LinkedHashMap<>();
		Map<EventId, Set<EventId>> singles = new LinkedHashMap<>();
		for (BigStoryline row : big) {
			List<EventId> ofRow = this.storylines.events(row.storyline().id())
				.stream()
				.map(member -> member.event().id())
				.toList();
			rowEvents.put(row.storyline().id(), ofRow);
			ofRow.forEach(event -> singles.put(event, Set.of(event)));
		}
		Map<EventId, EventTally> tallies = this.events.tallies(singles, read);
		Map<EventId, List<ArticleId>> inPeriod = new LinkedHashMap<>();
		for (EventId event : singles.keySet()) {
			if (tallies.get(event).articles() > 0) {
				inPeriod.put(event, this.events.articles(event)
					.stream()
					.filter(article -> !article.publishedAt().isBefore(read.from())
							&& article.publishedAt().isBefore(read.to()))
					.map(EventArticleView::articleId)
					.toList());
			}
		}
		Optional<List<ArticleFacts>> facts = facts(
				inPeriod.values().stream().flatMap(List::stream).distinct().toList(), interests.tagSet());
		if (facts.isEmpty()) {
			return Optional.empty();
		}
		Map<ArticleId, ArticleFacts> eligible = new HashMap<>();
		facts.get().stream().filter(filter::passesExclusions).forEach(article -> eligible.put(article.id(), article));
		Map<EventId, List<ArticleId>> eligibleGroups = new LinkedHashMap<>();
		inPeriod.forEach((event, ids) -> {
			List<ArticleId> ofEvent = ids.stream().filter(eligible::containsKey).toList();
			if (!ofEvent.isEmpty()) {
				eligibleGroups.put(event, ofEvent);
			}
		});
		Map<EventId, ArticleId> representatives = eligibleGroups.isEmpty() ? Map.of()
				: this.events.representatives(eligibleGroups);
		List<BigRow> rows = new ArrayList<>(rowEvents.size());
		rowEvents.forEach((storyline, ofRow) -> rows.add(new BigRow(ofRow.stream()
			.filter(inPeriod::containsKey)
			.map(event -> {
				ArticleId representative = representatives.get(event);
				return new BigEvent(event, tallies.get(event).importanceSum(),
						(representative == null) ? null : eligible.get(representative));
			})
			.toList())));
		return Optional.of(BigSlotPicker.pick(rows, interests.bigSlots(),
				SelectionRules.bigSeats(interests.maxCandidates())));
	}

	/**
	 * 기사들의 재료 — 평가를 활성 키로 푼 것({@link ActiveEvaluations})에 발행 시각 · 소스를 얹는다. 그 사이 지운 기사는 빠진다.
	 * @return 계보 표의 활성 버전이 관심 설정의 버전과 다르면 빈 값
	 */
	private Optional<List<ArticleFacts>> facts(List<ArticleId> ids, TagSetId active) {
		Optional<Map<ArticleId, ActiveEvaluation>> resolved = this.activeEvaluations.read(ids, active);
		if (resolved.isEmpty()) {
			return Optional.empty();
		}
		Map<ArticleId, ActiveEvaluation> evaluated = resolved.get();
		if (evaluated.isEmpty()) {
			return Optional.of(List.of());
		}
		Map<ArticleId, Instant> published = this.articles.publishedAt(evaluated.keySet());
		Map<ArticleId, Set<SourceId>> sources = this.articles.sources(evaluated.keySet());
		List<ArticleFacts> facts = new ArrayList<>(evaluated.size());
		for (ActiveEvaluation evaluation : evaluated.values()) {
			Instant publishedAt = published.get(evaluation.id());
			Set<SourceId> articleSources = sources.get(evaluation.id());
			if (publishedAt == null || articleSources == null) {
				continue;
			}
			facts.add(new ArticleFacts(evaluation.id(), evaluation.subtopics(), evaluation.regions(),
					evaluation.formats(), evaluation.topicStatus(), evaluation.importance(), articleSources,
					publishedAt));
		}
		return Optional.of(facts);
	}

	/** 고른 후보마다 토큰 어림 — 제목 · 요약문 길이. 내용을 읽지 못한(그 사이 지운) 기사는 빠진다 */
	private Map<ArticleId, Integer> tokens(Shortlist shortlist) {
		List<ArticleId> ids = new ArrayList<>();
		shortlist.big().forEach(article -> ids.add(article.id()));
		shortlist.subscribed().forEach(article -> ids.add(article.id()));
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<ArticleId, Integer> tokens = new HashMap<>();
		for (ArticleContent content : this.articles.findAll(ids).values()) {
			String summary = Objects.requireNonNullElse(content.summary(), "");
			tokens.put(content.id(), CandidateCost.candidate(codePoints(content.title()), codePoints(summary)));
		}
		return tokens;
	}

	private static int codePoints(String text) {
		return text.codePointCount(0, text.length());
	}

	private static Instant latest(Instant a, Instant b) {
		return a.isAfter(b) ? a : b;
	}

}
