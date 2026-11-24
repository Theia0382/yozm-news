package net.yozmnews.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import net.yozmnews.TestClocks;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.digest.InterestHistory;
import net.yozmnews.digest.InterestHistorySpan;
import net.yozmnews.digest.InterestPoint;
import net.yozmnews.digest.InterestTimeline;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.InterestShift;
import net.yozmnews.report.ReportDirectory;
import net.yozmnews.report.ReportFixtures;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.adapter.out.fake.FakeReportGenerator;
import net.yozmnews.report.domain.content.ReportSectionRepository;
import net.yozmnews.report.domain.input.CoverageSection;
import net.yozmnews.report.domain.input.Count;
import net.yozmnews.report.domain.input.EventLine;
import net.yozmnews.report.domain.input.FlowInterestLine;
import net.yozmnews.report.domain.input.FlowMaterial;
import net.yozmnews.report.domain.input.FlowSummary;
import net.yozmnews.report.domain.input.InputSection;
import net.yozmnews.report.domain.input.InterestsSection;
import net.yozmnews.report.domain.input.PriorReport;
import net.yozmnews.report.domain.input.PriorSection;
import net.yozmnews.report.domain.input.ReportInput;
import net.yozmnews.report.domain.input.StorylinesSection;
import net.yozmnews.report.domain.input.TopicCoverageLine;
import net.yozmnews.report.domain.input.TopicInterestLine;
import net.yozmnews.report.domain.report.ReportPeriod;
import net.yozmnews.report.domain.report.ReportRepository;
import net.yozmnews.report.domain.view.ReportViewRepository;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.tag.TagKeyLineage;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagSetNames;
import net.yozmnews.tag.TagSetThresholds;
import net.yozmnews.tag.TagSetView;
import net.yozmnews.tag.TopicCoverage;
import net.yozmnews.user.DigestInterests;
import net.yozmnews.user.DigestInterestsDirectory;
import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.StorylineSubscriptionCause;
import net.yozmnews.user.StorylineSubscriptionChange;
import net.yozmnews.user.StorylineSubscriptionDirectory;
import net.yozmnews.user.SummaryLength;
import net.yozmnews.user.SummaryUsage;
import net.yozmnews.user.TopicSubscription;
import net.yozmnews.user.UserStorylineDirectory;
import net.yozmnews.user.UserStorylineId;
import net.yozmnews.user.UserStorylineSummaries;
import net.yozmnews.user.UserStorylineSummaryRef;
import net.yozmnews.user.UserStorylineSummaryView;
import net.yozmnews.user.UserStorylineView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 재료 조립 — 갈래 셋의 재료 · 직전 리포트의 스냅숏과 글 · 주간 리포트 고르기 · 재료 없음 · 계정 없음 · 태그 체계 버전 어긋남.
 * <p>
 * {@code tag} · {@code storyline} · {@code event} · {@code article} · {@code report} 는 실제 조회와 저장소다. 계정과 관심 신호 이력이 있어야 하는
 * {@code user} 의 관심 설정 · 사용자 스토리라인 · 정리 · 추적 이력과 {@code digest} 의 관심사 히스토리는 그 모듈의 공개 조회 계약대로 값을 돌려주는
 * 대역이다 — 그 조회의 판정은 각 모듈의 시험이 지킨다. 지키는 규칙: report-R14 ~ report-R21
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ReportMaterialCollectionTests {

	private static final UserId USER = new UserId(7);

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	/** 서울 시간대 11-05 ~ 11-11 주간 */
	private static final ReportPeriod WEEK = new ReportPeriod(ReportPeriodKind.WEEK,
			Instant.parse("2026-11-04T15:00:00Z"), Instant.parse("2026-11-11T15:00:00Z"));

	/** 직전 주간 — 끝이 이번 시작 */
	private static final ReportPeriod PREVIOUS = new ReportPeriod(ReportPeriodKind.WEEK,
			Instant.parse("2026-10-28T15:00:00Z"), Instant.parse("2026-11-04T15:00:00Z"));

	private static final Instant SUMMARIZED = Instant.parse("2026-11-03T00:00:00Z");

	/** 정리만 남은 흐름의 헤드 — 사용자 스토리라인 조회에는 없고 저장한 정리에만 있다 */
	private static final StorylineId SUMMARY_ONLY = new StorylineId(999_999_999L);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	TagKeyLineage lineage;

	@Autowired
	TopicCoverage coverage;

	@Autowired
	StorylineDirectory storylines;

	@Autowired
	EventDirectory events;

	@Autowired
	ArticleDirectory articles;

	@Autowired
	ReportViewRepository views;

	@Autowired
	ReportDirectory reports;

	@Autowired
	ReportRepository reportRows;

	@Autowired
	ReportSectionRepository sections;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private TagSetId active;

	private String topic;

	private String topicName;

	private long storylineA;

	private long storylineB;

	/** 추적하지 않는 사용자 스토리라인의 헤드 — 관심 줄에는 들고 추적한 흐름에는 들지 않는다 */
	private long storylineC;

	private long eventFirst;

	private long eventSecond;

	@BeforeEach
	void plantStorylines() {
		clear();
		this.active = this.tagSets.activeTagSet().id();
		TagSetNames names = this.tagSets.names(this.active);
		this.topic = names.subtopics().getFirst().key();
		this.topicName = names.subtopics().getFirst().name();
		long criteria = this.jdbc.sql("""
				insert into event_assignment_criteria (window_seconds, upper, lower, store, probability, created_at)
				values (259200, 0.8, 0.55, 0.4, 0.5, now()) returning id
				""").query(Long.class).single();
		// 기간 안 사건 둘(기사 하나씩), 기간 앞 사건 하나
		this.eventFirst = event("금리 인상 발표", "2026-11-05T01:00:00Z", criteria);
		this.eventSecond = event("환율 급등", "2026-11-06T01:00:00Z", criteria);
		long eventBefore = event("기간 앞 사건", "2026-11-01T01:00:00Z", criteria);
		this.storylineA = storyline("2026-11-01T01:00:00Z", "2026-11-05T01:00:00Z", this.eventFirst);
		member(this.storylineA, eventBefore);
		member(this.storylineA, this.eventFirst);
		this.storylineB = storyline("2026-11-06T01:00:00Z", "2026-11-06T01:00:00Z", this.eventSecond);
		member(this.storylineB, this.eventSecond);
		this.storylineC = storyline("2026-11-01T01:00:00Z", "2026-11-01T01:00:00Z", eventBefore);
		member(this.storylineC, eventBefore);
	}

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from reports").update();
		StorylineRows.clear(this.jdbc);
	}

	@Test
	void collectsTheThreeKindsWithThePreviousSnapshotAndText() {
		ReportId previous = previousReport(USER, """
				{"version": 1, "tagSet": %d,
				 "period": {"kind": "WEEK", "start": "2026-10-28T15:00:00Z", "end": "2026-11-04T15:00:00Z"},
				 "storylines": {"basis": "PUBLISHED", "flows": [{"head": %d, "tops": [%d], "articles": 5, "previous": null,
				   "trend": "NO_COMPARISON", "trackedInPeriod": false}]},
				 "coverage": {"basis": "FIRST_EVALUATED", "topics": [{"key": "%s", "name": "%s", "articles": 4,
				   "previous": null, "trend": "NO_COMPARISON"}]}}"""
			.formatted(this.active.value(), this.storylineA, this.storylineA, this.topic, this.topicName));
		// 같은 기간의 뒤 행이라도 시작하지 않은 리포트는 직전 리포트가 아니다(report-R18)
		generation().schedule(USER, PREVIOUS);

		ReportInput input = collection(this.tagSets).collect(USER, WEEK, List.of()).orElseThrow();

		assertThat(input.user()).isEqualTo(USER);
		assertThat(input.period()).isEqualTo(WEEK);
		assertThat(input.zone()).isEqualTo(SEOUL);
		assertThat(input.sections()).extracting(InputSection::kind)
			.containsExactly(ReportSectionKind.STORYLINES, ReportSectionKind.COVERAGE, ReportSectionKind.INTERESTS);
		StorylineId head = new StorylineId(this.storylineB);
		FlowSummary summary = new FlowSummary("금리 흐름", "흐름 요약", "최근 전개", SUMMARIZED);
		// 흐름 — 꼭대기 둘의 기간 안 기사 2(기간 앞 사건은 빠진다), 직전 5 → 줄었다, 기간 안에 사용자가 추적을 더했다
		// 추적 중인 사용자 스토리라인만 — 추적하지 않는 흐름(헤드 C)은 이 갈래에 없다(report-R14 · report-R16)
		assertThat(((StorylinesSection) input.sections().get(0)).flows()).hasSize(1);
		FlowMaterial flow = ((StorylinesSection) input.sections().get(0)).flows().getFirst();
		assertThat(flow.head()).isEqualTo(head);
		assertThat(flow.tops()).containsExactlyInAnyOrder(new StorylineId(this.storylineA), head);
		assertThat(flow.summary()).isEqualTo(summary);
		assertThat(flow.headState()).isEqualTo(this.storylines.find(head).orElseThrow().state());
		assertThat(flow.trackedInPeriod()).isTrue();
		assertThat(flow.articles()).isEqualTo(new Count(2, 5L));
		assertThat(flow.events()).containsExactly(
				new EventLine(new EventId(this.eventFirst), "금리 인상 발표", LocalDate.of(2026, 11, 5), 1, true),
				new EventLine(new EventId(this.eventSecond), "환율 급등", LocalDate.of(2026, 11, 6), 1, true));
		// 구독 주제 — 기간 안 평가가 없어 0, 직전 4
		assertThat(((CoverageSection) input.sections().get(1)).topics())
			.containsExactly(new TopicCoverageLine(this.topic, this.topicName, new Count(0, 4L)));
		// 관심 — 주제 생김, 흐름은 헤드의 값. 흐름 줄은 사용자 스토리라인 전부(추적하지 않는 C 포함)와 정리만 남은 흐름의 합집합(report-R17)
		InterestsSection interests = (InterestsSection) input.sections().get(2);
		assertThat(interests.topics())
			.containsExactly(new TopicInterestLine(this.topic, this.topicName, 0.1, 0.3, InterestShift.ENTERED));
		assertThat(interests.flows()).containsExactly(new FlowInterestLine(head, summary, null, 0.6, InterestShift.NONE),
				new FlowInterestLine(new StorylineId(this.storylineC), null, null, 0.4, InterestShift.NONE),
				new FlowInterestLine(SUMMARY_ONLY, new FlowSummary("지난 흐름", "지난 요약", "지난 전개", SUMMARIZED), 0.5, null,
						InterestShift.LEFT));
		assertThat(interests.subscriptionChanges()).isEmpty();
		// 직전 리포트 — 시작할 때 행에 남긴다
		assertThat(input.previous()).isEqualTo(previous);
		// 직전 리포트의 보이는 글
		assertThat(input.priors()).containsExactly(new PriorReport(previous, ReportPeriodKind.WEEK, PREVIOUS.start(),
				PREVIOUS.end(), "로컬 대체물이 만든 리포트다. 섹션 1개.",
				List.of(new PriorSection(ReportSectionKind.STORYLINES, "로컬 섹션 1", "로컬 대체물이 만든 본문이다."))));
		// 스냅숏 — 이은 직전 값과 판정
		assertThat(JSON.readTree(input.statsSnapshot())).isEqualTo(JSON.readTree("""
				{"version": 1, "tagSet": %d,
				 "period": {"kind": "WEEK", "start": "2026-11-04T15:00:00Z", "end": "2026-11-11T15:00:00Z"},
				 "storylines": {"basis": "PUBLISHED", "flows": [{"head": %d, "tops": [%d, %d], "articles": 2,
				   "previous": 5, "trend": "FELL", "trackedInPeriod": true}]},
				 "coverage": {"basis": "FIRST_EVALUATED", "topics": [{"key": "%s", "name": "%s", "articles": 0,
				   "previous": 4, "trend": "FELL"}]},
				 "interests": {"topics": [{"key": "%s", "name": "%s", "start": 0.1, "end": 0.3, "shift": "ENTERED"}],
				   "flows": [{"head": %d, "start": null, "end": 0.6, "shift": "NONE"},
				             {"head": %d, "start": null, "end": 0.4, "shift": "NONE"},
				             {"head": 999999999, "start": 0.5, "end": null, "shift": "LEFT"}]}}"""
			.formatted(this.active.value(), this.storylineB, this.storylineA, this.storylineB, this.topic, this.topicName,
					this.topic, this.topicName, this.storylineB, this.storylineC)));
	}

	@Test
	void leavesTheComparisonEmptyWithoutAPreviousReport() {
		ReportInput input = collection(this.tagSets).collect(USER, WEEK, List.of()).orElseThrow();

		assertThat(((StorylinesSection) input.sections().get(0)).flows().getFirst().articles()).isEqualTo(new Count(2, null));
		assertThat(((CoverageSection) input.sections().get(1)).topics().getFirst().articles()).isEqualTo(new Count(0, null));
		assertThat(input.priors()).isEmpty();
		assertThat(input.previous()).isNull();
	}

	@Test
	void carriesThePreviousReportEvenWhenItsSnapshotCannotBeRead() {
		ReportId previous = previousReport(USER, "{\"version\": 2}");

		ReportInput input = collection(this.tagSets).collect(USER, WEEK, List.of()).orElseThrow();

		assertThat(input.previous()).isEqualTo(previous);
		assertThat(((CoverageSection) input.sections().get(1)).topics().getFirst().articles()).isEqualTo(new Count(0, null));
	}

	@Test
	void takesOnlyTheReadyWeeklyReportsOfTheUser() {
		ReportId ready = previousReport(USER, "{}");
		ReportId others = previousReport(new UserId(8), "{}");
		ReportId scheduled = generation().schedule(USER, PREVIOUS);

		ReportInput input = collection(this.tagSets)
			.collect(USER, new ReportPeriod(ReportPeriodKind.MONTH, WEEK.start(), WEEK.end()), List.of(ready, others, scheduled))
			.orElseThrow();

		assertThat(input.priors()).extracting(PriorReport::id).containsExactly(ready);
	}

	@Test
	void hasNoSectionWithoutSubscriptionsTrackedFlowsAndInterestAndNoInputWithoutAnAccount() {
		ReportMaterialCollection bare = new ReportMaterialCollection(this.tagSets, this.lineage, this.coverage,
				user -> user.equals(USER) ? Optional.of(interests(List.of())) : Optional.empty(),
				(user, from, to) -> List.of(), new Summaries(List.of()), new Changes(List.of()),
				this.storylines, this.events, this.articles, new History(quiet(WEEK.start()), quiet(WEEK.end())),
				this.views, this.reports);

		assertThat(bare.collect(USER, WEEK, List.of()).orElseThrow().sections()).isEmpty();
		assertThat(bare.collect(new UserId(99), WEEK, List.of())).isEmpty();
	}

	@Test
	void aMismatchedActiveVersionIsRetriedOnceAndThenRefused() {
		TagSetId elsewhere = new TagSetId(this.active.value() + 1_000);
		AtomicInteger skewed = new AtomicInteger(1);

		assertThat(collection(new Skewed(this.tagSets, skewed, elsewhere)).collect(USER, WEEK, List.of())).isPresent();
		assertThat(skewed).hasValue(0);

		AtomicInteger always = new AtomicInteger(Integer.MAX_VALUE);
		assertThatIllegalStateException()
			.isThrownBy(() -> collection(new Skewed(this.tagSets, always, elsewhere)).collect(USER, WEEK, List.of()))
			.withMessageContaining("2 번 어긋났다");
	}

	private ReportMaterialCollection collection(TagSetDirectory tagSets) {
		StorylineId head = new StorylineId(this.storylineB);
		Set<StorylineId> tops = Set.of(new StorylineId(this.storylineA), head);
		UserStorylineId id = new UserStorylineId(1);
		StorylineId untracked = new StorylineId(this.storylineC);
		SummaryUsage usage = new SummaryUsage("openai", "gpt-5.5-mini", 1, 1, 1, 1, 1);
		return new ReportMaterialCollection(tagSets, this.lineage, this.coverage,
				user -> user.equals(USER) ? Optional.of(interests(List.of(new TopicSubscription(this.topic,
						Set.of(this.topic))))) : Optional.empty(),
				(user, from, to) -> List.of(new UserStorylineView(id, head, tops, true),
						new UserStorylineView(new UserStorylineId(2), untracked, Set.of(untracked), false)),
				new Summaries(List.of(
						new UserStorylineSummaryView(id, head, "금리 흐름", "흐름 요약", "최근 전개", true, tops, List.of(), usage,
								SUMMARIZED, SUMMARIZED),
						new UserStorylineSummaryView(new UserStorylineId(3), SUMMARY_ONLY, "지난 흐름", "지난 요약", "지난 전개",
								false, Set.of(SUMMARY_ONLY), List.of(), usage, SUMMARIZED, SUMMARIZED))),
				new Changes(List.of(new StorylineSubscriptionChange(new StorylineId(this.storylineA),
						new StorylineId(this.storylineA), StorylineSubscriptionAction.ADD, StorylineSubscriptionCause.USER,
						Instant.parse("2026-11-06T00:00:00Z")))),
				this.storylines, this.events, this.articles,
				new History(
						new InterestPoint(WEEK.start(), Map.of(this.topic, 0.1), Set.of(), Set.of(), List.of(),
								Map.of(SUMMARY_ONLY, 0.5), Set.of(), Set.of()),
						new InterestPoint(WEEK.end(), Map.of(this.topic, 0.3), Set.of(this.topic), Set.of(), List.of(),
								Map.of(head, 0.6, untracked, 0.4), Set.of(), Set.of(SUMMARY_ONLY))),
				this.views, this.reports);
	}

	private DigestInterests interests(List<TopicSubscription> topics) {
		return new DigestInterests(this.active, topics, Set.of(), Set.of(), Set.of(), "", SEOUL, 20, 3,
				SummaryLength.NORMAL, ReasoningEffort.MEDIUM);
	}

	private static InterestPoint quiet(Instant at) {
		return new InterestPoint(at, Map.of(), Set.of(), Set.of(), List.of(), Map.of(), Set.of(), Set.of());
	}

	/** 직전 주간 리포트 — 로컬 대체물로 READY, 스냅숏은 손으로 쓴 문자열 */
	private ReportId previousReport(UserId user, String snapshot) {
		ReportGeneration generation = generation();
		ReportId id = generation.schedule(user, PREVIOUS);
		generation.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, new ReportInput(user, PREVIOUS, SEOUL,
				List.of(new StorylinesSection(List.of(ReportFixtures.flow(this.storylineA)))), List.of(), snapshot, null));
		return id;
	}

	private ReportGeneration generation() {
		return new ReportGeneration(this.reportRows, this.sections, new FakeReportGenerator(), this.transaction,
				TestClocks.of(() -> Instant.parse("2026-11-12T00:00:00Z")));
	}

	private long event(String title, String published, long criteria) {
		long article = EventArticles.insert(this.jdbc, title, null);
		long event = StorylineRows.event(this.jdbc, "OPEN", "model-a", "[1,0]", 1, published, published, Map.of());
		this.jdbc.sql("""
				insert into event_articles (article_id, event_id, published_at, score, decided_by, opened, criteria_id,
				                            assigned_at)
				values (:article, :event, :published, 0.9, 'FORMULA', false, :criteria, '2026-11-06T00:00:00Z')
				""")
			.param("article", article)
			.param("event", event)
			.param("published", Instant.parse(published).atOffset(ZoneOffset.UTC))
			.param("criteria", criteria)
			.update();
		return event;
	}

	private long storyline(String first, String last, long representative) {
		return this.jdbc.sql("""
				insert into storylines (model_version, centroid, article_count, first_article_at, last_article_at,
				                        representative_event_id, created_at, updated_at)
				values ('model-a', cast('[1,0]' as halfvec), 1, :first, :last, :representative, '2026-11-06T00:00:00Z',
				        '2026-11-06T00:00:00Z')
				returning id
				""")
			.param("first", Instant.parse(first).atOffset(ZoneOffset.UTC))
			.param("last", Instant.parse(last).atOffset(ZoneOffset.UTC))
			.param("representative", representative)
			.query(Long.class)
			.single();
	}

	private void member(long storyline, long event) {
		this.jdbc.sql("""
				insert into storyline_members (storyline_id, event_id, linked_by, linked_at)
				values (:storyline, :event, 'MERGE', '2026-11-06T00:00:00Z')
				""").param("storyline", storyline).param("event", event).update();
	}

	/** 정리 조회 대역 — 저장한 정리 목록을 그대로 낸다 */
	private record Summaries(List<UserStorylineSummaryView> shown) implements UserStorylineSummaries {

		@Override
		public List<UserStorylineSummaryView> shown(UserId user) {
			return this.shown;
		}

		@Override
		public Optional<UserStorylineSummaryView> find(UserId user, UserStorylineId id) {
			return this.shown.stream().filter(summary -> summary.id().equals(id)).findFirst();
		}

		@Override
		public Map<StorylineId, List<UserStorylineSummaryRef>> ofTops(UserId user,
				java.util.Collection<StorylineId> tops) {
			throw new UnsupportedOperationException("재료 조립은 꼭대기로 정리를 찾지 않는다");
		}

	}

	/** 추적 이력 대역 — 기간 안 이력 행을 그대로 낸다 */
	private record Changes(List<StorylineSubscriptionChange> rows) implements StorylineSubscriptionDirectory {

		@Override
		public List<StorylineId> subscriptions(UserId user) {
			throw new UnsupportedOperationException("재료 조립은 추적 씨앗을 읽지 않는다");
		}

		@Override
		public List<StorylineSubscriptionChange> changes(UserId user, Instant from, Instant to) {
			return this.rows;
		}

	}

	/** 관심사 히스토리 대역 — at 의 둘째 · 셋째 기준 시각(시작 · 끝)의 점을 낸다 */
	private record History(InterestPoint start, InterestPoint end) implements InterestHistory {

		@Override
		public Optional<InterestTimeline> recent(UserId user, InterestHistorySpan span) {
			throw new UnsupportedOperationException("재료 조립은 최근 기간별 관심을 읽지 않는다");
		}

		@Override
		public Optional<List<InterestPoint>> at(UserId user, List<Instant> references) {
			if (!user.equals(USER)) {
				return Optional.empty();
			}
			assertThat(references).containsExactly(WEEK.start(), WEEK.start(), WEEK.end());
			return Optional.of(List.of(this.start, this.end));
		}

	}

	/** 활성 버전을 정해진 횟수만큼 다른 버전으로 내는 태그 체계 조회 */
	private record Skewed(TagSetDirectory delegate, AtomicInteger remaining, TagSetId elsewhere) implements TagSetDirectory {

		@Override
		public TagSetView activeTagSet() {
			TagSetView view = this.delegate.activeTagSet();
			if (this.remaining.get() > 0) {
				this.remaining.decrementAndGet();
				return new TagSetView(this.elsewhere, view.number(), view.topics(), view.regions(), view.format(),
						view.importance());
			}
			return view;
		}

		@Override
		public TagSetThresholds activeThresholds() {
			return this.delegate.activeThresholds();
		}

		@Override
		public TagSetView tagSet(TagSetId id) {
			return this.delegate.tagSet(id);
		}

		@Override
		public TagSetNames names(TagSetId id) {
			return this.delegate.names(id);
		}

	}

}
