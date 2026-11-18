package net.yozmnews.digest.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleHeadline;
import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.digest.DigestPeriod;
import net.yozmnews.digest.domain.input.CandidateArticle;
import net.yozmnews.digest.domain.input.DigestInput;
import net.yozmnews.digest.domain.input.DigestProfile;
import net.yozmnews.digest.domain.input.StorylineGroup;
import net.yozmnews.digest.domain.selection.ArticleFacts;
import net.yozmnews.digest.domain.selection.BigSlotPicker;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigEvent;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigRow;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigSlots;
import net.yozmnews.digest.domain.selection.BudgetCut;
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
import net.yozmnews.tag.ArticleEvaluationView;
import net.yozmnews.tag.ArticleEvaluations;
import net.yozmnews.tag.EvaluationPeriod;
import net.yozmnews.tag.TagKeyLineage;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagProbability;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagSetNames;
import net.yozmnews.user.DigestInterests;
import net.yozmnews.user.DigestInterestsDirectory;
import net.yozmnews.user.TopicSubscription;
import org.springframework.stereotype.Service;

/**
 * 다이제스트 후보 선별 — 다른 모듈의 공개 조회로 재료를 읽어 {@code domain/selection} 의 순수 단계에 넘기고, 고른 후보에 프롬프트 재료를
 * 붙여 생성의 입력({@link DigestInput})으로 낸다 — docs/specs/digest.md §4
 * <p>
 * 트랜잭션 없이 조회를 차례로 부른다 — 한 스냅숏이 아니다. 그 사이 지운 기사는 재료에서 빠지고, 생성이 후보 행으로 다시 거른다(digest-R9).
 * 관심 설정의 키 버전과 기사 평가 키를 푼 계보 표의 활성 버전이 다르면(조회 사이의 태그 체계 활성 전환) 처음부터 한 번 더 하고, 그래도 다르면
 * {@link IllegalStateException} 이다. 계정이 없으면 빈 값이다. 백그라운드 작업 전용이다 — 화면 경로에서 부르지 않는다.
 * <p>
 * 후보의 제목 · 요약문은 토큰 어림에 쓴 읽기의 값을 그대로 입력에 싣는다 — 어림과 프롬프트의 글이 같다(ADR-0210).
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

	private final TagKeyLineage lineage;

	private final TagSetDirectory tagSets;

	private final ArticleDirectory articles;

	private final EventDirectory events;

	private final StorylineDirectory storylines;

	CandidateSelection(DigestInterestsDirectory interests, ArticleEvaluations evaluations, TagKeyLineage lineage,
			TagSetDirectory tagSets, ArticleDirectory articles, EventDirectory events, StorylineDirectory storylines) {
		this.interests = Objects.requireNonNull(interests, "interests");
		this.evaluations = Objects.requireNonNull(evaluations, "evaluations");
		this.lineage = Objects.requireNonNull(lineage, "lineage");
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
		this.articles = Objects.requireNonNull(articles, "articles");
		this.events = Objects.requireNonNull(events, "events");
		this.storylines = Objects.requireNonNull(storylines, "storylines");
	}

	/**
	 * @param period 다이제스트 기간 — 시각 범위. 사용자 시간대의 날로 만드는 일은 부르는 쪽이 한다
	 * @param model 쓸 모델 — 입력 토큰 예산
	 * @return 생성의 입력 — 후보는 큰 슬롯 기사(선정 이유 {@code MAJOR_STORYLINE}) 다음 구독 후보({@code SUBSCRIBED}). 후보가 없어도
	 * 프로필이 있는 입력이다. 계정이 없으면 빈 값
	 * @throws IllegalStateException 관심 설정과 기사 평가 키의 활성 버전이 {@link #ATTEMPTS} 번 모두 어긋났을 때
	 */
	public Optional<DigestInput> select(UserId user, DigestPeriod period, LlmModel model) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(period, "period");
		Objects.requireNonNull(model, "model");
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			Optional<Attempt> selected = attempt(user, period, model);
			if (selected.isPresent()) {
				return selected.get().input();
			}
		}
		throw new IllegalStateException("관심 설정과 기사 평가 키의 태그 체계 활성 버전이 " + ATTEMPTS + " 번 어긋났다");
	}

	/** 한 번의 선별 — 활성 버전이 어긋나면 빈 값 */
	private Optional<Attempt> attempt(UserId user, DigestPeriod period, LlmModel model) {
		Optional<DigestInterests> found = this.interests.interestsOf(user);
		if (found.isEmpty()) {
			return Optional.of(new Attempt(Optional.empty()));
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
		Optional<BigPick> big = bigSlots(interests, period, filter);
		if (big.isEmpty()) {
			return Optional.empty();
		}
		Shortlist shortlist = CandidateSelector.shortlist(ranked, eventOf, representatives, big.get().slots(), filter,
				interests.maxCandidates());
		Map<ArticleId, ArticleContent> contents = contents(shortlist);
		Map<Integer, String> titles = storylineTitles(CandidateSelector.storylineRows(shortlist), big.get().rows(),
				contents);
		int budget = CandidateCost.candidateBudget(model.inputTokenBudget(), codePoints(interests.interestText()),
				interests.topics().size(), interests.excludedRegions().size(),
				CandidateCost.storylines(titles.values().stream().map(CandidateSelection::codePoints).toList()));
		Shortlist cut = BudgetCut.apply(shortlist, tokens(contents), budget);
		return Optional.of(new Attempt(Optional.of(input(interests, cut, contents, titles))));
	}

	/** 자른 후보에 프롬프트 재료를 붙인다 — 이름은 관심 설정 버전(활성 버전)의 이름이다 */
	private DigestInput input(DigestInterests interests, Shortlist cut, Map<ArticleId, ArticleContent> contents,
			Map<Integer, String> titles) {
		TagSetNames names = this.tagSets.names(interests.tagSet());
		Map<ArticleId, ArticleFacts> facts = new HashMap<>();
		cut.big().forEach(article -> facts.put(article.id(), article));
		cut.subscribed().forEach(article -> facts.put(article.id(), article));
		List<DigestCandidate> candidates = CandidateSelector.candidates(cut);
		Map<ArticleId, ArticleHeadline> headlines = candidates.isEmpty() ? Map.of()
				: this.articles.headlines(candidates.stream().map(DigestCandidate::articleId).toList());
		List<CandidateArticle> articles = new ArrayList<>(candidates.size());
		for (DigestCandidate candidate : candidates) {
			ArticleContent content = contents.get(candidate.articleId());
			ArticleFacts fact = facts.get(candidate.articleId());
			ArticleHeadline headline = headlines.get(candidate.articleId());
			articles.add(new CandidateArticle(candidate, content.title(), content.summary(),
					(headline == null) ? List.of() : headline.sources(), content.publishedAt(),
					ordered(fact.subtopics(), names.topicOrder(), names::topicName),
					ordered(fact.regions(), names.regionOrder(), names::regionName)));
		}
		List<StorylineGroup> storylines = new ArrayList<>();
		CandidateSelector.storylineRows(cut)
			.forEach((row, ids) -> storylines.add(new StorylineGroup(titles.get(row), ids)));
		DigestProfile profile = new DigestProfile(
				ordered(interests.topics().stream().map(TopicSubscription::key).toList(), names.topicOrder(),
						names::topicName),
				ordered(interests.excludedRegions(), names.regionOrder(), names::regionName), interests.interestText(),
				interests.timezone(), interests.summaryLength(), interests.effort());
		return new DigestInput(profile, articles, storylines);
	}

	/**
	 * 큰 판정 행마다 묶음의 이름 — 그 스토리라인 대표 사건의 대표 기사 제목. 읽지 못하면(그 사이 지운 기사) 묶음 첫 기사의 제목
	 * @param rows 행 위치 → 그 행의 큰 슬롯 기사(자르기 전)
	 * @param bigRows 큰 판정 행 — 위치가 {@code rows} 의 키
	 * @param contents 큰 슬롯 기사의 내용
	 */
	private Map<Integer, String> storylineTitles(Map<Integer, List<ArticleId>> rows, List<BigStoryline> bigRows,
			Map<ArticleId, ArticleContent> contents) {
		if (rows.isEmpty()) {
			return Map.of();
		}
		Map<Integer, EventId> representativeEvents = new LinkedHashMap<>();
		rows.keySet().forEach(row -> representativeEvents.put(row, bigRows.get(row).storyline().representativeEvent()));
		Map<EventId, ArticleId> representatives = this.events.representativesOfAll(representativeEvents.values());
		Map<ArticleId, ArticleHeadline> headlines = representatives.isEmpty() ? Map.of()
				: this.articles.headlines(representatives.values());
		Map<Integer, String> titles = new LinkedHashMap<>();
		rows.forEach((row, ids) -> {
			ArticleId representative = representatives.get(representativeEvents.get(row));
			ArticleHeadline headline = (representative == null) ? null : headlines.get(representative);
			if (headline != null) {
				titles.put(row, headline.title());
				return;
			}
			ids.stream()
				.map(contents::get)
				.filter(Objects::nonNull)
				.findFirst()
				.ifPresent(content -> titles.put(row, content.title()));
		});
		return titles;
	}

	/**
	 * 키의 이름을 체계 순서로 늘어놓는다.
	 * @throws IllegalStateException 이름이 없는 키일 때 — 키는 모두 관심 설정 버전의 것이어야 한다
	 */
	private static List<String> ordered(Collection<String> keys, Map<String, Integer> order,
			Function<String, Optional<String>> name) {
		return keys.stream()
			.sorted(Comparator.comparing((String key) -> order.getOrDefault(key, Integer.MAX_VALUE))
				.thenComparing(Comparator.naturalOrder()))
			.map(key -> name.apply(key)
				.orElseThrow(() -> new IllegalStateException("태그 체계에 이름이 없는 키다: " + key)))
			.toList();
	}

	/**
	 * 큰 슬롯 — 기간의 큰 판정 목록(보도 강도 순)의 행마다 기간 안 기사가 있는 사건과 그 중요도 합, 사건마다 기간 안 기사 가운데 평가가 있고
	 * 사용자의 제외를 지나는 기사의 대표를 읽어 {@link BigSlotPicker} 에 넘긴다. 조회 기간은 끝에서 {@link EventPeriod#MAX_LENGTH} 까지로
	 * 줄인다. 큰 슬롯 수가 0 이면 읽지 않는다.
	 * @return 고른 큰 슬롯과 큰 판정 행 — 계보 표의 활성 버전이 관심 설정의 버전과 다르면 빈 값
	 */
	private Optional<BigPick> bigSlots(DigestInterests interests, DigestPeriod period, UserFilter filter) {
		if (interests.bigSlots() <= 0) {
			return Optional.of(BigPick.NONE);
		}
		Instant from = latest(period.start(), period.end().minus(EventPeriod.MAX_LENGTH));
		EventPeriod read = EventPeriod.of(from, period.end()).orElseThrow();
		List<BigStoryline> big = this.storylines.bigStorylines(read);
		if (big.isEmpty()) {
			return Optional.of(BigPick.NONE);
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
		return Optional.of(new BigPick(
				BigSlotPicker.pick(rows, interests.bigSlots(), SelectionRules.bigSeats(interests.maxCandidates())),
				big));
	}

	/**
	 * 기사들의 재료 — 평가 · 발행 시각 · 소스를 읽고 평가 키를 활성 키로 푼다. 그 사이 지운 기사는 빠진다.
	 * @return 계보 표의 활성 버전이 관심 설정의 버전과 다르면 빈 값
	 */
	private Optional<List<ArticleFacts>> facts(List<ArticleId> ids, TagSetId active) {
		Map<ArticleId, ArticleEvaluationView> views = evaluations(ids);
		if (views.isEmpty()) {
			return Optional.of(List.of());
		}
		Map<TagSetId, TagKeyMapping> mappings = new HashMap<>();
		Set<TagSetId> versions = views.values()
			.stream()
			.map(ArticleEvaluationView::tagSetId)
			.collect(Collectors.toSet());
		for (TagSetId version : versions) {
			TagKeyMapping mapping = this.lineage.toActive(version);
			if (!mapping.to().equals(active)) {
				return Optional.empty();
			}
			mappings.put(version, mapping);
		}
		Map<ArticleId, Instant> published = this.articles.publishedAt(views.keySet());
		Map<ArticleId, Set<SourceId>> sources = this.articles.sources(views.keySet());
		List<ArticleFacts> facts = new ArrayList<>(views.size());
		for (ArticleEvaluationView view : views.values()) {
			Instant publishedAt = published.get(view.articleId());
			Set<SourceId> articleSources = sources.get(view.articleId());
			if (publishedAt == null || articleSources == null) {
				continue;
			}
			TagKeyMapping mapping = mappings.get(view.tagSetId());
			facts.add(new ArticleFacts(view.articleId(), resolve(mapping, TagKind.TOPIC, view.topics()),
					resolve(mapping, TagKind.REGION, view.regions()), mapping.resolve(TagKind.FORMAT, view.format()),
					view.topicStatus(), view.importance(), articleSources, publishedAt));
		}
		return Optional.of(facts);
	}

	/** {@link ArticleEvaluations#MAX_ARTICLES} 씩 나눠 읽는다 */
	private Map<ArticleId, ArticleEvaluationView> evaluations(List<ArticleId> ids) {
		Map<ArticleId, ArticleEvaluationView> views = new LinkedHashMap<>();
		for (int i = 0; i < ids.size(); i += ArticleEvaluations.MAX_ARTICLES) {
			int end = Math.min(ids.size(), i + ArticleEvaluations.MAX_ARTICLES);
			views.putAll(this.evaluations.findAll(ids.subList(i, end)));
		}
		return views;
	}

	/** 고른 후보의 내용 — 토큰 어림과 프롬프트 재료가 같은 읽기를 쓴다. 내용을 읽지 못한(그 사이 지운) 기사는 빠진다 */
	private Map<ArticleId, ArticleContent> contents(Shortlist shortlist) {
		List<ArticleId> ids = new ArrayList<>();
		shortlist.big().forEach(article -> ids.add(article.id()));
		shortlist.subscribed().forEach(article -> ids.add(article.id()));
		return ids.isEmpty() ? Map.of() : this.articles.findAll(ids);
	}

	/** 내용마다 토큰 어림 — 제목 · 요약문 길이 */
	private static Map<ArticleId, Integer> tokens(Map<ArticleId, ArticleContent> contents) {
		Map<ArticleId, Integer> tokens = new HashMap<>();
		for (ArticleContent content : contents.values()) {
			String summary = Objects.requireNonNullElse(content.summary(), "");
			tokens.put(content.id(), CandidateCost.candidate(codePoints(content.title()), codePoints(summary)));
		}
		return tokens;
	}

	private static Set<String> resolve(TagKeyMapping mapping, TagKind kind, List<TagProbability> tags) {
		return tags.stream()
			.flatMap(tag -> mapping.resolve(kind, tag.key()).stream())
			.collect(Collectors.toUnmodifiableSet());
	}

	private static int codePoints(String text) {
		return text.codePointCount(0, text.length());
	}

	private static Instant latest(Instant a, Instant b) {
		return a.isAfter(b) ? a : b;
	}

	/** 한 번의 선별 결과 — 계정이 없으면 빈 입력 */
	private record Attempt(Optional<DigestInput> input) {
	}

	/**
	 * 고른 큰 슬롯과 그 재료인 큰 판정 행
	 * @param rows 큰 판정 행 — 위치가 {@link BigSlots#rows} 의 값
	 */
	private record BigPick(BigSlots slots, List<BigStoryline> rows) {

		static final BigPick NONE = new BigPick(BigSlots.NONE, List.of());

	}

}
