package net.yozmnews.digest.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import net.yozmnews.digest.domain.input.InputEvent;
import net.yozmnews.digest.domain.input.InputItem;
import net.yozmnews.digest.domain.interest.InterestLevels;
import net.yozmnews.digest.domain.interest.InterestRecord;
import net.yozmnews.digest.domain.interest.InterestWeights;
import net.yozmnews.digest.domain.interest.InterestWindow;
import net.yozmnews.digest.domain.interest.Interests;
import net.yozmnews.digest.domain.item.ArrangedItem;
import net.yozmnews.digest.domain.item.ItemGrouping;
import net.yozmnews.digest.domain.item.ItemPlan;
import net.yozmnews.digest.domain.selection.ActiveEvaluation;
import net.yozmnews.digest.domain.selection.ArticleFacts;
import net.yozmnews.digest.domain.selection.BigRowOrder;
import net.yozmnews.digest.domain.selection.BigSlotPicker;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigEvent;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigRow;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigSlots;
import net.yozmnews.digest.domain.selection.BudgetCut;
import net.yozmnews.digest.domain.selection.CandidateCost;
import net.yozmnews.digest.domain.selection.CandidateEvent;
import net.yozmnews.digest.domain.selection.CandidateEvents;
import net.yozmnews.digest.domain.selection.CandidateSelector;
import net.yozmnews.digest.domain.selection.EventFacts;
import net.yozmnews.digest.domain.selection.EventSample;
import net.yozmnews.digest.domain.selection.FollowedStorylines;
import net.yozmnews.digest.domain.selection.SelectedEvent;
import net.yozmnews.digest.domain.selection.SelectionRules;
import net.yozmnews.digest.domain.selection.Shortlist;
import net.yozmnews.digest.domain.selection.SubscriptionFilter;
import net.yozmnews.digest.domain.selection.UserFilter;
import net.yozmnews.event.EventArticleView;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.event.EventTally;
import net.yozmnews.feedback.EventFeedback;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.platform.UserId;
import net.yozmnews.source.SourceId;
import net.yozmnews.storyline.BigStoryline;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.ArticleEvaluations;
import net.yozmnews.tag.EvaluationPeriod;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagSetNames;
import net.yozmnews.user.DigestInterests;
import net.yozmnews.user.DigestInterestsDirectory;
import net.yozmnews.user.TopicSubscription;
import org.springframework.stereotype.Service;

/**
 * 다이제스트 후보 선별 — 다른 모듈의 공개 조회로 재료를 읽어 {@code domain/selection} · {@code domain/item} 의 순수 단계에 넘기고, 고른
 * 사건을 항목으로 묶어 표본 기사에 프롬프트 재료를 붙여 생성의 입력({@link DigestInput})으로 낸다 — docs/specs/digest.md §4
 * <p>
 * 후보는 사건이다. 기간에 첫 평가된 기사 가운데 사건에 배정된 기사만 쓰고, 사건마다 평가 있는 소속 기사 전부로 사건의 값({@link EventFacts})을
 * 센다 — 기간 · 제외와 무관하다. 사건의 기사 가운데 그 기간에 든 · 기사 제외를 지난 기사가 표본({@link EventSample})의 모집단이다.
 * <p>
 * 트랜잭션 없이 조회를 차례로 부른다 — 한 스냅숏이 아니다. 그 사이 지운 기사는 재료에서 빠지고, 생성이 후보 행으로 다시 거른다(digest-R9).
 * 관심 설정의 키 버전과 기사 평가 키를 푼 계보 표의 활성 버전이 다르면(조회 사이의 태그 체계 활성 전환) 처음부터 한 번 더 하고, 그래도 다르면
 * {@link IllegalStateException} 이다 — 관심도 재료({@link InterestMaterials})의 키 버전도 같다. 계정이 없으면 빈 값이다. 백그라운드 작업
 * 전용이다 — 화면 경로에서 부르지 않는다.
 * <p>
 * 순위는 관심도(기준 시각 = 기간 끝 — 주제 × 스토리라인 가중치)를 곱하고, 그 사용자의 값이 싫어요인 사건과 거른 꼭대기만 가진 사건은
 * 후보에서 뺀다 — 구독 사건과 큰 슬롯 사건이 같은 판정({@link UserFilter#passesEvent})을 쓴다. 싫어요는 구독 후보의 사건을 필터를
 * 만들기 전에, 큰 슬롯 사건을 그 사건이 정해진 뒤에 따로 묻는다.
 * 구독 사건은 같은 재료를 중립 순위로도 골라 견준다 — 사건의 값은 한 번 읽고, 표본 내용(토큰 어림)은 두 결과의 사건을 함께 읽는다.
 * <p>
 * 표본 기사의 제목 · 요약문은 토큰 어림에 쓴 읽기의 값을 그대로 입력에 싣는다 — 어림과 프롬프트의 글이 같다(ADR-0210).
 * <p>
 * 주제 구독 밖 관심 흐름의 사건은 추적 중인 사용자 스토리라인의 꼭대기로 관심 흐름을 정하고({@link FollowedStorylines}) 구독 사건의
 * 자리에서 몫 상한 안으로 먼저 담는다 — 중립 결과에는 그 길이 없어 그 사건은 {@code INTEREST} 다.
 * <p>
 * 큰 슬롯은 큰 판정 목록의 행을 그대로 쓴다. 목록은 포함 나무마다 한 행이라 한 포함 나무는 슬롯 하나를 쓴다 — 행의 사건에는 그
 * 아래 하위의 사건이 함께 든다. 앞 행이 쓴 사건은 뒤 행에서 다시 들지 않는다.
 * <p>
 * 기간 사건의 포함 나무 꼭대기({@link StorylineDirectory#topsOf})는 한 번 읽어 후보 사건에 싣고(큰 슬롯 사건은 모자란 것만 더 읽는다)
 * 순위의 스토리라인 가중치 · 거른 꼭대기 · 큰 판정 행 건너뛰기 · 항목 묶기가 함께 쓴다. 한 번에 묻는 상한씩 나눠 묻는다.
 */
@Service
public class CandidateSelection {

	private final DigestInterestsDirectory interests;

	private final ArticleEvaluations evaluations;

	private final ActiveEvaluations activeEvaluations;

	private final TagSetDirectory tagSets;

	private final ArticleDirectory articles;

	private final EventDirectory events;

	private final StorylineDirectory storylines;

	private final InterestMaterials interestMaterials;

	private final EventFeedback feedback;

	private final InstantSource clock;

	CandidateSelection(DigestInterestsDirectory interests, ArticleEvaluations evaluations,
			ActiveEvaluations activeEvaluations, TagSetDirectory tagSets, ArticleDirectory articles,
			EventDirectory events, StorylineDirectory storylines, InterestMaterials interestMaterials,
			EventFeedback feedback, InstantSource clock) {
		this.interests = Objects.requireNonNull(interests, "interests");
		this.evaluations = Objects.requireNonNull(evaluations, "evaluations");
		this.activeEvaluations = Objects.requireNonNull(activeEvaluations, "activeEvaluations");
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
		this.articles = Objects.requireNonNull(articles, "articles");
		this.events = Objects.requireNonNull(events, "events");
		this.storylines = Objects.requireNonNull(storylines, "storylines");
		this.interestMaterials = Objects.requireNonNull(interestMaterials, "interestMaterials");
		this.feedback = Objects.requireNonNull(feedback, "feedback");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * @param period 다이제스트 기간 — 시각 범위. 사용자 시간대의 날로 만드는 일은 부르는 쪽이 한다
	 * @param model 쓸 모델 — 입력 토큰 예산
	 * @return 생성의 입력 — 항목은 주요 뉴스 다음 헤드라인, 각각 항목 순위. 사건의 선정 이유는 큰 슬롯 사건 {@code MAJOR_STORYLINE},
	 *     구독 사건은 관심도로 들어왔으면 {@code INTEREST} 아니면 {@code SUBSCRIBED}, 주제 구독 밖 관심 흐름의 길로 든 사건은
	 *     {@code INTEREST}. 후보가 없어도 프로필이 있는 입력이다. 계정이 없으면 빈 값
	 * @throws IllegalStateException 관심 설정과 기사 평가 키의 활성 버전이 {@link ActiveEvaluations#ATTEMPTS} 번 모두 어긋났을 때
	 */
	public Optional<DigestInput> select(UserId user, DigestPeriod period, LlmModel model) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(period, "period");
		Objects.requireNonNull(model, "model");
		for (int attempt = 0; attempt < ActiveEvaluations.ATTEMPTS; attempt++) {
			Optional<Attempt> selected = attempt(user, period, model);
			if (selected.isPresent()) {
				return selected.get().input();
			}
		}
		throw new IllegalStateException(
				"관심 설정과 기사 평가 키의 태그 체계 활성 버전이 " + ActiveEvaluations.ATTEMPTS + " 번 어긋났다");
	}

	/** 한 번의 선별 — 활성 버전이 어긋나면 빈 값 */
	private Optional<Attempt> attempt(UserId user, DigestPeriod period, LlmModel model) {
		Optional<DigestInterests> found = this.interests.interestsOf(user);
		if (found.isEmpty()) {
			return Optional.of(new Attempt(Optional.empty()));
		}
		DigestInterests interests = found.get();
		Instant now = this.clock.instant();
		Instant interestFrom = InterestWindow.start(period.end());
		Optional<InterestRecord> interestRecord = this.interestMaterials.read(user, interests.tagSet(), interestFrom,
				now.isAfter(interestFrom) ? now : interestFrom);
		if (interestRecord.isEmpty()) {
			return Optional.empty();
		}
		Interests levels = InterestWindow.levels(interestRecord.get(), period.end(), now);
		InterestWeights weights = InterestWeights.of(levels);
		Instant from = latest(period.start(), period.end().minus(EvaluationPeriod.MAX_LENGTH));
		EvaluationPeriod readPeriod = EvaluationPeriod.of(from, period.end()).orElseThrow();
		Optional<List<ArticleFacts>> facts = facts(this.evaluations.firstEvaluatedIn(readPeriod), interests.tagSet());
		if (facts.isEmpty()) {
			return Optional.empty();
		}
		Map<ArticleId, EventId> eventOf = facts.get().isEmpty() ? Map.of()
				: this.events.eventsOf(facts.get().stream().map(ArticleFacts::id).toList());
		Optional<Map<EventId, EventFacts>> eventFacts = eventFacts(new LinkedHashSet<>(eventOf.values()), Map.of(),
				interests.tagSet());
		if (eventFacts.isEmpty()) {
			return Optional.empty();
		}
		Map<EventId, List<StorylineId>> tops = new HashMap<>(
				StorylineTopsQueries.ofEvents(this.storylines, eventFacts.get().keySet()));
		UserFilter filter = UserFilter.of(interests, disliked(user, eventOf.values()), levels.storylines().excluded());
		List<CandidateEvent> pool = CandidateEvents.subscribed(facts.get(), eventOf, eventFacts.get(), tops, filter,
				from);
		List<CandidateEvent> ranked = SubscriptionFilter.select(pool, filter, weights);
		List<CandidateEvent> neutral = SubscriptionFilter.select(pool, filter, InterestWeights.NEUTRAL_ALL);
		Optional<BigPick> big = bigSlots(user, interests, period, filter, levels.topics(), weights, eventFacts.get(),
				tops);
		if (big.isEmpty()) {
			return Optional.empty();
		}
		// 추적 중인 사용자 스토리라인의 꼭대기 — 추적한 흐름과 그 흐름이 이어짐으로 따라가는 뒤 흐름
		Set<StorylineId> followed = levels.storylines().followed(interestRecord.get().flows().trackedTops());
		Set<EventId> taken = big.get()
			.slots()
			.events()
			.stream()
			.map(event -> event.facts().id())
			.collect(Collectors.toSet());
		List<CandidateEvent> byFlow = FollowedStorylines.pick(pool, filter, weights, followed, taken);
		Shortlist shortlist = CandidateSelector.shortlist(ranked, big.get().slots(), filter, interests.maxCandidates(),
				byFlow);
		Shortlist neutralShortlist = CandidateSelector.shortlist(neutral, big.get().slots(), filter,
				interests.maxCandidates(), List.of());
		Map<EventId, List<ArticleFacts>> samples = samples(shortlist, neutralShortlist);
		Map<ArticleId, ArticleContent> contents = contents(samples);
		int budget = CandidateCost.candidateBudget(model.inputTokenBudget(), codePoints(interests.interestText()),
				interests.topics().size(), interests.excludedRegions().size());
		Map<EventId, Integer> tokens = tokens(samples, contents);
		Shortlist cut = BudgetCut.apply(shortlist, tokens, budget);
		Shortlist neutralCut = BudgetCut.apply(neutralShortlist, tokens, budget);
		List<SelectedEvent> selected = CandidateSelector.candidates(cut, neutralCut, weights);
		Map<Integer, StorylineId> bigTops = new LinkedHashMap<>();
		for (SelectedEvent event : selected) {
			Integer row = event.bigRow();
			if (row != null) {
				bigTops.put(row, big.get().tops().get(row));
			}
		}
		List<ArrangedItem> items = ItemPlan.arrange(ItemGrouping.group(selected, bigTops), weights);
		return Optional.of(new Attempt(Optional.of(input(interests, items, samples, contents))));
	}

	/**
	 * 항목마다 사건 · 표본 기사에 프롬프트 재료를 붙인다 — 내용을 읽지 못한(그 사이 지운) 표본 기사는 빠진다. 예산 자르기가 남긴 사건은 내용을
	 * 읽은 표본 기사가 하나 이상이다. 이름은 관심 설정 버전(활성 버전)의 이름이다
	 */
	private DigestInput input(DigestInterests interests, List<ArrangedItem> items,
			Map<EventId, List<ArticleFacts>> samples, Map<ArticleId, ArticleContent> contents) {
		TagSetNames names = this.tagSets.names(interests.tagSet());
		List<ArticleId> cited = new ArrayList<>();
		for (ArrangedItem item : items) {
			for (SelectedEvent event : item.plan().events()) {
				samples.get(event.event().facts().id())
					.stream()
					.map(ArticleFacts::id)
					.filter(contents::containsKey)
					.forEach(cited::add);
			}
		}
		Map<ArticleId, ArticleHeadline> headlines = cited.isEmpty() ? Map.of() : this.articles.headlines(cited);
		List<InputItem> inputItems = new ArrayList<>(items.size());
		for (ArrangedItem item : items) {
			List<InputEvent> inputEvents = new ArrayList<>(item.plan().events().size());
			for (SelectedEvent event : item.plan().events()) {
				List<CandidateArticle> articles = new ArrayList<>();
				for (ArticleFacts fact : samples.get(event.event().facts().id())) {
					ArticleContent content = contents.get(fact.id());
					if (content == null) {
						continue;
					}
					ArticleHeadline headline = headlines.get(fact.id());
					articles.add(new CandidateArticle(new DigestCandidate(fact.id(), event.reason()), content.title(),
							content.summary(), (headline == null) ? List.of() : headline.sources(), content.publishedAt(),
							ordered(fact.subtopics(), names.topicOrder(), names::topicName),
							ordered(fact.regions(), names.regionOrder(), names::regionName)));
				}
				inputEvents.add(new InputEvent(event.event().facts().id(), event.reason(), articles));
			}
			inputItems.add(new InputItem(item.kind(), inputEvents, item.plan().top()));
		}
		DigestProfile profile = new DigestProfile(
				ordered(interests.topics().stream().map(TopicSubscription::key).toList(), names.topicOrder(),
						names::topicName),
				ordered(interests.excludedRegions(), names.regionOrder(), names::regionName), interests.interestText(),
				interests.timezone(), interests.summaryLength(), interests.effort());
		return new DigestInput(profile, inputItems);
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
	 * 큰 슬롯 — 기간의 큰 판정 목록(보도 강도 순)의 행마다 기간 안 기사가 있는 사건과 그 중요도 합을 읽고, 그 사건들의 값(평가 있는 소속
	 * 기사 전부)과 기간 안 기사 가운데 평가가 있는 기사로 후보 사건({@link CandidateEvents#big})을 꾸려 {@link BigSlotPicker} 에 넘긴다.
	 * 기간 안 기사가 있는 사건의 싫어요를 물어 필터에 더하고, 꼭대기를 모르는 사건의 꼭대기를 {@code tops} 에 더해 후보 사건에 싣는다. 주제
	 * 관심의 평균이 거름 기준 이하인 사건은 후보가 없다({@link InterestLevels#excluded}). 행의 머리마다 포함 나무의 꼭대기를 한 번에
	 * 물어({@link StorylineDirectory#topsOfStorylines}) 행에 싣고, 그 꼭대기가 거른 꼭대기인 행은 {@link BigSlotPicker} 가 건너뛴다. 행은
	 * 보도 강도에 관심 가중치를 곱한 순서({@link BigRowOrder})로 넘기고, 큰 판정 행의 꼭대기 목록도 그 순서다.
	 * 기간 안 기사가 없는 사건은 읽지 않는다. 조회 기간은 끝에서 {@link EventPeriod#MAX_LENGTH} 까지로 줄인다. 큰 슬롯 수가 0 이면 읽지
	 * 않는다.
	 * @param topicLevels 그 사용자의 주제 관심 — 큰 슬롯 사건의 주제 거름
	 * @param weights 그 사용자의 관심 가중치 — 행 순서
	 * @param known 이미 읽은 사건의 값 — 다시 읽지 않는다
	 * @param tops 이미 읽은 사건의 꼭대기 — 행의 사건 가운데 없는 것을 읽어 더한다(항목 묶기가 같은 맵을 쓴다)
	 * @return 고른 큰 슬롯과 큰 판정 행 — 계보 표의 활성 버전이 관심 설정의 버전과 다르면 빈 값
	 */
	private Optional<BigPick> bigSlots(UserId user, DigestInterests interests, DigestPeriod period, UserFilter filter,
			InterestLevels topicLevels, InterestWeights weights, Map<EventId, EventFacts> known,
			Map<EventId, List<StorylineId>> tops) {
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
		Map<StorylineId, BigDecimal> intensities = new HashMap<>();
		Map<EventId, Set<EventId>> singles = new LinkedHashMap<>();
		for (BigStoryline row : big) {
			intensities.put(row.storyline().id(), row.intensity());
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
		Optional<Map<EventId, EventFacts>> eventFacts = eventFacts(inPeriod.keySet(), known, interests.tagSet());
		if (eventFacts.isEmpty()) {
			return Optional.empty();
		}
		Optional<List<ArticleFacts>> facts = facts(inPeriod.values().stream().flatMap(List::stream).distinct().toList(),
				interests.tagSet());
		if (facts.isEmpty()) {
			return Optional.empty();
		}
		tops.putAll(StorylineTopsQueries.ofEvents(this.storylines,
				inPeriod.keySet().stream().filter(event -> !tops.containsKey(event)).toList()));
		UserFilter bigFilter = filter.withDisliked(disliked(user, inPeriod.keySet()));
		Map<ArticleId, ArticleFacts> evaluated = new HashMap<>();
		facts.get().forEach(article -> evaluated.put(article.id(), article));
		Map<StorylineId, StorylineId> rowTops = StorylineTopsQueries.ofStorylines(this.storylines, rowEvents.keySet());
		List<BigRow> rows = new ArrayList<>(rowEvents.size());
		rowEvents.forEach((storyline, ofRow) -> rows.add(BigRow.resolved(storyline, rowTops,
				intensities.get(storyline), ofRow.stream()
			.filter(inPeriod::containsKey)
			.map(event -> {
				EventFacts ofEvent = eventFacts.get().get(event);
				List<ArticleFacts> articles = inPeriod.get(event)
					.stream()
					.map(evaluated::get)
					.filter(Objects::nonNull)
					.toList();
				return new BigEvent(event, tallies.get(event).importanceSum(),
						(ofEvent == null) ? null
								: CandidateEvents.big(ofEvent, tops.getOrDefault(event, List.of()), articles, bigFilter,
										topicLevels));
			})
			.toList())));
		List<BigRow> ordered = BigRowOrder.order(rows, weights);
		return Optional.of(new BigPick(
				BigSlotPicker.pick(ordered, interests.bigSlots(), SelectionRules.bigSeats(interests.maxCandidates()),
						bigFilter),
				ordered.stream().map(BigRow::top).toList()));
	}

	/**
	 * 사건들의 값 — 사건마다 배정된 기사 전부의 평가로 센다({@link ActiveEvaluations#eventFacts}). 평가 있는 소속 기사가 없는 사건은
	 * 빠진다
	 * @param known 이미 읽은 사건의 값 — 그대로 결과에 넣고 다시 읽지 않는다
	 * @return 사건 → 사건의 값({@code known} 포함). 계보 표의 활성 버전이 관심 설정의 버전과 다르면 빈 값
	 */
	private Optional<Map<EventId, EventFacts>> eventFacts(Collection<EventId> ids, Map<EventId, EventFacts> known,
			TagSetId active) {
		Map<EventId, EventFacts> result = new HashMap<>(known);
		List<EventId> missing = ids.stream().filter(id -> !known.containsKey(id)).distinct().toList();
		if (missing.isEmpty()) {
			return Optional.of(result);
		}
		Optional<Map<EventId, EventFacts>> read = this.activeEvaluations.eventFacts(this.events.articleIdsOf(missing),
				active);
		if (read.isEmpty()) {
			return Optional.empty();
		}
		result.putAll(read.get());
		return Optional.of(result);
	}

	/**
	 * 기사들의 재료 — 평가를 활성 키로 푼 것({@link ActiveEvaluations})에 발행 시각 · 소스를 얹는다. 평가가 없거나 그 사이 지운 기사는
	 * 빠진다.
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
			facts.add(ArticleFacts.of(evaluation, articleSources, publishedAt));
		}
		return Optional.of(facts);
	}

	/** 사건 가운데 그 사용자의 값이 싫어요인 사건 — {@link EventFeedback#MAX_EVENTS} 씩 나눠 묻는다 */
	private Set<EventId> disliked(UserId user, Collection<EventId> events) {
		List<EventId> ids = events.stream().distinct().toList();
		Set<EventId> disliked = new HashSet<>();
		for (int i = 0; i < ids.size(); i += EventFeedback.MAX_EVENTS) {
			int end = Math.min(ids.size(), i + EventFeedback.MAX_EVENTS);
			disliked.addAll(this.feedback.dislikedAmong(user, ids.subList(i, end)));
		}
		return disliked;
	}

	/** 두 결과의 사건마다 표본 — 큰 슬롯 · 구독 · 새 길 사건, 사건 ID 순으로 한 번씩 */
	private static Map<EventId, List<ArticleFacts>> samples(Shortlist ranked, Shortlist neutral) {
		Map<EventId, List<ArticleFacts>> samples = new LinkedHashMap<>();
		for (Shortlist shortlist : List.of(ranked, neutral)) {
			for (List<CandidateEvent> events : List.of(shortlist.big(), shortlist.subscribed(), shortlist.followed())) {
				events.forEach(event -> samples.computeIfAbsent(event.facts().id(), id -> EventSample.of(event)));
			}
		}
		return samples;
	}

	/**
	 * 표본 기사의 내용 — 토큰 어림과 프롬프트 재료가 같은 읽기를 쓴다. 내용을 읽지 못한(그 사이 지운) 기사는 빠진다
	 */
	private Map<ArticleId, ArticleContent> contents(Map<EventId, List<ArticleFacts>> samples) {
		List<ArticleId> ids = samples.values().stream().flatMap(List::stream).map(ArticleFacts::id).distinct().toList();
		return ids.isEmpty() ? Map.of() : this.articles.findAll(ids);
	}

	/**
	 * 사건마다 토큰 어림 — 내용을 읽은 표본 기사마다 기사 줄 어림(제목 · 요약문 길이)을 더한다. 내용을 읽은 표본 기사가 없는 사건은 빠진다
	 */
	private static Map<EventId, Integer> tokens(Map<EventId, List<ArticleFacts>> samples,
			Map<ArticleId, ArticleContent> contents) {
		Map<EventId, Integer> tokens = new HashMap<>();
		samples.forEach((event, articles) -> {
			List<Integer> lines = new ArrayList<>();
			for (ArticleFacts article : articles) {
				ArticleContent content = contents.get(article.id());
				if (content != null) {
					String summary = Objects.requireNonNullElse(content.summary(), "");
					lines.add(CandidateCost.candidate(codePoints(content.title()), codePoints(summary)));
				}
			}
			if (!lines.isEmpty()) {
				tokens.put(event, CandidateCost.event(lines));
			}
		});
		return tokens;
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
	 * 고른 큰 슬롯과 큰 판정 행마다 머리의 포함 나무 꼭대기
	 * @param tops 관심을 곱한 순서의 큰 판정 행마다 {@link BigRow#top} — 위치가 {@link BigSlots#rows} 의 값
	 */
	private record BigPick(BigSlots slots, List<StorylineId> tops) {

		static final BigPick NONE = new BigPick(BigSlots.NONE, List.of());

	}

}
