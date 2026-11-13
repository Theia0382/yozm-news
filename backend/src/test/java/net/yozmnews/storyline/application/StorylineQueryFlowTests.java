package net.yozmnews.storyline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.event.EventTally;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.storyline.BigStoryline;
import net.yozmnews.storyline.BigStorylineAdministration;
import net.yozmnews.storyline.BigStorylineCriteria;
import net.yozmnews.storyline.BigStorylineCriteriaRows;
import net.yozmnews.storyline.EventStoryline;
import net.yozmnews.storyline.StorylineCursor;
import net.yozmnews.storyline.StorylineEvent;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylinePage;
import net.yozmnews.storyline.StorylineRelationKind;
import net.yozmnews.storyline.StorylineRelationView;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.storyline.StorylineState;
import net.yozmnews.storyline.StorylineSummary;
import net.yozmnews.storyline.StorylineView;
import net.yozmnews.storyline.UnlinkedEvents;
import net.yozmnews.storyline.domain.big.BigStorylineCriteriaRepository;
import net.yozmnews.storyline.domain.relation.Relation;
import net.yozmnews.storyline.domain.relation.RelationCriteria;
import net.yozmnews.storyline.domain.relation.RelationKind;
import net.yozmnews.storyline.domain.relation.RelationRepository;
import net.yozmnews.storyline.domain.storyline.StateCriteria;
import net.yozmnews.storyline.domain.storyline.StorylineQueryRepository;
import net.yozmnews.storyline.domain.storyline.StorylineRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 스토리라인 공개 조회와 큰 판정의 흐름 — 기사 · 엔트리 · 평가 · 사건 · 배정 · 스토리라인 · 소속 행을 SQL 로 넣고, 공개 조회가 {@code event} 의
 * 공개 조회로 기간 안 사건과 사건 묶음의 기간 집계를 읽어 기간의 스토리라인을 고르고 판정하는지, 합쳐진 스토리라인 · 사건의 ID 를 남는 쪽으로
 * 따르는지, 타임라인 · 목록의 쪽 넘기기 · 상태 · 소속 없는 사건을 실제 PostgreSQL 에서 본다.
 * <p>
 * 큰 판정 기준은 인스턴스에 한 행이라 시작과 끝에 비우고 처음 값을 다시 적재한다. 사건 · 스토리라인 행과 이 시험이 넣은 평가 행도 시작과 끝에
 * 지운다. 상태는 고정 시계로 본다.
 * <p>
 * 지키는 규칙: storyline-R16 · storyline-R19 ~ R23 · storyline-R25 · storyline-R33
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class StorylineQueryFlowTests {

	/** 기간 [11-05T00, 11-06T00) */
	private static final EventPeriod PERIOD = new EventPeriod(Instant.parse("2026-11-05T00:00:00Z"),
			Instant.parse("2026-11-06T00:00:00Z"));

	private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

	private static final RelationCriteria RELATION_CRITERIA = new RelationCriteria(0.60, 0.20, 3, 0.88,
			Duration.ofDays(14));

	@Autowired
	StorylineQueryRepository storylines;

	@Autowired
	StorylineRepository memberships;

	@Autowired
	BigStorylineCriteriaRepository criteria;

	@Autowired
	RelationRepository relations;

	@Autowired
	BigStorylineAdministration administration;

	@Autowired
	EventDirectory events;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private final List<Long> evaluated = new ArrayList<>();

	private StorylineDirectoryService directory;

	private long assignment;

	private long eventA;

	private long eventB;

	private long eventC;

	private long eventOld;

	private long eventLoose;

	private long eventMerged;

	private long wide;

	private long narrow;

	private long quiet;

	private long ended;

	private long spanning;

	@BeforeEach
	void startFromTheFixture() {
		clear();
		new BigStorylineCriteriaSeeding(this.criteria, this.transaction, InstantSource.system()).seedIfAbsent();
		// 이 픽스처의 기간에는 소스 넷(1 · 2 · 3 · 5)에 기사가 있다 — 기준 2.00 이면 중요도 합 8 부터 크다
		this.administration.change(new BigDecimal("2.00"), ChangeActor.OPERATOR);
		// 잠잠 7일 · 끝남 30일, 지금은 11-10
		this.directory = new StorylineDirectoryService(this.storylines, this.memberships, this.relations, this.criteria,
				this.events,
				new StateCriteria(Duration.ofDays(7), Duration.ofDays(30)), InstantSource.fixed(NOW));
		this.assignment = assignmentCriteria();
		long assignment = this.assignment;
		// 사건 A — 기간 안 기사 둘(소스 1 · 2, 중요도 5.0 · 5.0)
		this.eventA = event("2026-11-05T01:00:00Z", "2026-11-05T02:00:00Z");
		assign(article(1, 5.0), this.eventA, assignment, "2026-11-05T01:00:00Z");
		assign(article(2, 5.0), this.eventA, assignment, "2026-11-05T02:00:00Z");
		// 사건 B — 기간 안 기사 둘(소스 2 · 3, 중요도 2.0 · 없음)
		this.eventB = event("2026-11-05T03:00:00Z", "2026-11-05T04:00:00Z");
		assign(article(2, 2.0), this.eventB, assignment, "2026-11-05T03:00:00Z");
		assign(article(3, null), this.eventB, assignment, "2026-11-05T04:00:00Z");
		// 사건 C — 기간 안 기사 하나(소스 1, 중요도 5.0)
		this.eventC = event("2026-11-05T05:00:00Z", "2026-11-05T05:00:00Z");
		assign(article(1, 5.0), this.eventC, assignment, "2026-11-05T05:00:00Z");
		// 지난 사건 — 기간 밖 기사 하나
		this.eventOld = event("2026-11-03T00:00:00Z", "2026-11-03T00:00:00Z");
		assign(article(4, 5.0), this.eventOld, assignment, "2026-11-03T00:00:00Z");
		// 소속 없는 사건 — 기간 안 기사 넷
		this.eventLoose = event("2026-11-05T06:00:00Z", "2026-11-05T09:00:00Z");
		for (int hour = 6; hour <= 9; hour++) {
			assign(article(5, 1.0), this.eventLoose, assignment, "2026-11-05T0" + hour + ":00:00Z");
		}
		// A 에 합쳐졌는데 소속을 아직 옮기지 않은 사건
		this.eventMerged = event("2026-11-05T00:30:00Z", "2026-11-05T00:30:00Z");
		StorylineRows.merge(this.jdbc, this.eventMerged, this.eventA);

		// wide = {A, B, 합쳐진 사건} — 사건마다는 소스 둘, 합치면 소스 셋 · 평균 4
		this.wide = storyline("2026-11-05T01:00:00Z", "2026-11-05T04:00:00Z", this.eventA);
		member(this.wide, this.eventA);
		member(this.wide, this.eventB);
		member(this.wide, this.eventMerged);
		// narrow = {C, 지난 사건} — 기간 안에서는 소스 하나
		this.narrow = storyline("2026-11-03T00:00:00Z", "2026-11-05T05:00:00Z", this.eventC);
		member(this.narrow, this.eventC);
		member(this.narrow, this.eventOld);
		// quiet · ended = {지난 사건} — 기간 안 기사가 없다. 마지막 기사 뒤 9일 · 40일
		this.quiet = storyline("2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z", this.eventOld);
		member(this.quiet, this.eventOld);
		this.ended = storyline("2026-10-01T00:00:00Z", "2026-10-01T00:00:00Z", this.eventOld);
		member(this.ended, this.eventOld);
		// spanning — 첫 ~ 마지막 기사 시각은 기간을 덮지만 기간 안 기사가 있는 구성 사건이 없다
		this.spanning = storyline("2026-11-04T00:00:00Z", "2026-11-07T00:00:00Z", this.eventOld);
		member(this.spanning, this.eventOld);
	}

	@AfterEach
	void endWithTheSeededFirstValue() {
		clear();
		new BigStorylineCriteriaSeeding(this.criteria, this.transaction, InstantSource.system()).seedIfAbsent();
	}

	@Test
	void aStorylineIsBigByTheTallyOfAllItsEventsInThePeriod() {
		List<BigStoryline> big = this.directory.bigStorylines(PERIOD);

		// wide 는 사건 A · B 의 중요도를 한데 더한다 — (5 + 5 + 2) ÷ (소스 4 × 1일) = 3.00. 사건 A 만으로는 2.50, B 만으로는 0.50 이다
		assertThat(big).extracting(storyline -> storyline.storyline().id().value()).containsExactly(this.wide);
		EventTally tally = big.getFirst().tally();
		assertThat(tally.articles()).isEqualTo(4);
		assertThat(tally.sources()).isEqualTo(3);
		assertThat(tally.importance()).isEqualByComparingTo("4");
		assertThat(tally.importanceSum()).isEqualByComparingTo("12");
		assertThat(big.getFirst().intensity()).hasToString("3.00");
		// 대조 — narrow 는 5 ÷ 4 = 1.25 라 기준 2.00 에 이르지 못한다. 분모의 소스에는 소속 없는 사건의 소스(5)도 든다
		// 대조 — 기간이 반나절이면 같은 기사로 보도 강도가 두 배다: 12 ÷ (4 × 0.5일) = 6.00
		assertThat(this.directory.bigStorylines(new EventPeriod(Instant.parse("2026-11-05T00:00:00Z"),
				Instant.parse("2026-11-05T12:00:00Z"))))
			.extracting(BigStoryline::intensity)
			.extracting(BigDecimal::toPlainString)
			.containsExactly("6.00", "2.50");
	}

	@Test
	void theListHoldsStorylinesWithArticlesInThePeriodPagedByTheLastArticleTime() {
		StorylinePage first = this.directory.list(PERIOD, null, 1);

		// 기간의 스토리라인은 narrow · wide 둘 — spanning · quiet · ended 는 그날 기사가 있는 구성 사건이 없다
		assertThat(first.storylines()).extracting(summary -> summary.storyline().id().value()).containsExactly(this.narrow);
		StorylineSummary narrowRow = first.storylines().getFirst();
		assertThat(narrowRow.tally()).isEqualTo(new EventTally(1, 1, new BigDecimal("5.0"), new BigDecimal("5.0")));
		assertThat(narrowRow.intensity()).hasToString("1.25");
		assertThat(narrowRow.big()).isFalse();
		assertThat(narrowRow.storyline().eventCount()).isEqualTo(2);
		assertThat(narrowRow.storyline().representativeEvent()).isEqualTo(new EventId(this.eventC));
		assertThat(first.next())
			.isEqualTo(new StorylineCursor(Instant.parse("2026-11-05T05:00:00Z"), new StorylineId(this.narrow)));

		StorylinePage second = this.directory.list(PERIOD, first.next(), 1);
		assertThat(second.storylines()).extracting(summary -> summary.storyline().id().value()).containsExactly(this.wide);
		assertThat(second.storylines().getFirst().intensity()).hasToString("3.00");
		assertThat(second.storylines().getFirst().big()).isTrue();
		assertThat(second.next()).isNull();

		assertThat(this.directory.list(PERIOD, null, 100).storylines())
			.extracting(summary -> summary.storyline().id().value())
			.containsExactly(this.narrow, this.wide);
		assertThat(this.directory.list(new EventPeriod(Instant.parse("2026-12-01T00:00:00Z"),
				Instant.parse("2026-12-02T00:00:00Z")), null, 10))
			.isEqualTo(new StorylinePage(List.of(), null));
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.list(PERIOD, null, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.list(PERIOD, null, 101));
	}

	@Test
	void theStateIsJudgedAtTheQueryTimeFromTheLastArticle() {
		assertThat(this.directory.find(new StorylineId(this.wide)).orElseThrow().state()).isEqualTo(StorylineState.ACTIVE);
		assertThat(this.directory.find(new StorylineId(this.quiet)).orElseThrow().state()).isEqualTo(StorylineState.QUIET);
		assertThat(this.directory.find(new StorylineId(this.ended)).orElseThrow().state()).isEqualTo(StorylineState.ENDED);
	}

	@Test
	void aMergedStorylineIsFoundAsItsSurvivor() {
		long merged = storyline("2026-11-02T00:00:00Z", "2026-11-02T00:00:00Z", this.eventA);
		this.jdbc.sql("update storylines set merged_into = :into, merged_at = now() where id = :id")
			.param("into", this.wide)
			.param("id", merged)
			.update();

		assertThat(this.directory.find(new StorylineId(merged))).isEqualTo(this.directory.find(new StorylineId(this.wide)));
		assertThat(this.directory.find(new StorylineId(merged)).orElseThrow().id()).isEqualTo(new StorylineId(this.wide));
		assertThat(this.directory.events(new StorylineId(merged))).extracting(event -> event.event().id().value())
			.containsExactly(this.eventA, this.eventB);
		assertThat(this.directory.find(new StorylineId(999_999_999L))).isEmpty();
		assertThat(this.directory.events(new StorylineId(999_999_999L))).isEmpty();
	}

	@Test
	void theTimelineIsInFirstArticleOrderWithoutEventsNotYetFollowed() {
		// 합쳐진 사건의 소속 행은 남아 있지만 타임라인에는 나오지 않는다 — 구성 사건 수는 소속 행 수다
		assertThat(this.directory.events(new StorylineId(this.wide))).extracting(event -> event.event().id().value())
			.containsExactly(this.eventA, this.eventB);
		// 포함이 없으면 모두 직접 소속이다
		assertThat(this.directory.events(new StorylineId(this.wide))).extracting(StorylineEvent::direct)
			.containsExactly(true, true);
		assertThat(this.directory.find(new StorylineId(this.wide)).orElseThrow().eventCount()).isEqualTo(3);
		// 첫 기사 시각 순 — 지난 사건이 사건 C 보다 먼저다(사건 ID 는 C 가 작다)
		assertThat(this.directory.events(new StorylineId(this.narrow))).extracting(event -> event.event().id().value())
			.containsExactly(this.eventOld, this.eventC);
	}

	@Test
	void theStorylinesOfAnEventFollowAMergedEventToItsSurvivor() {
		assertThat(this.directory.storylinesOf(new EventId(this.eventOld))).extracting(view -> view.storyline().id().value())
			.containsExactly(this.narrow, this.quiet, this.ended, this.spanning);
		// 포함이 없으면 모두 직접 든 스토리라인이고 상위가 없다
		assertThat(this.directory.storylinesOf(new EventId(this.eventOld))).extracting(EventStoryline::direct)
			.containsOnly(true);
		assertThat(this.directory.storylinesOf(new EventId(this.eventOld))).extracting(view -> view.storyline().parent())
			.containsOnlyNulls();
		assertThat(this.directory.storylinesOf(new EventId(this.eventMerged)))
			.extracting(view -> view.storyline().id().value())
			.containsExactly(this.wide);
		// 소속 행이 없는 합쳐진 사건 — 자기 ID 로는 찾을 소속이 없고, 남는 사건 C 의 소속으로만 나온다
		long followed = event("2026-11-05T05:30:00Z", "2026-11-05T05:30:00Z");
		StorylineRows.merge(this.jdbc, followed, this.eventC);
		assertThat(this.directory.storylinesOf(new EventId(followed))).extracting(view -> view.storyline().id().value())
			.containsExactly(this.narrow);
		assertThat(this.directory.storylinesOf(new EventId(this.eventLoose))).isEmpty();
		assertThat(this.directory.storylinesOf(new EventId(999_999_999L))).isEmpty();
	}

	@Test
	void aParentHoldsTheEventsOfItsChildrenAndMarksTheInheritedOnes() {
		contains(this.wide, this.narrow);

		// wide 의 닫은 사건 집합 = {A, B, 합쳐진 사건} ∪ narrow 의 {C, 지난 사건} — 타임라인에는 아직 따르지 않은 합쳐진 사건이 빠진다
		List<StorylineEvent> timeline = this.directory.events(new StorylineId(this.wide));
		assertThat(timeline).extracting(event -> event.event().id().value())
			.containsExactly(this.eventOld, this.eventA, this.eventB, this.eventC);
		assertThat(timeline).extracting(StorylineEvent::direct).containsExactly(false, true, true, false);
		// 스토리라인 값은 닫은 사건 집합으로 센다 — 사건 다섯, 사건마다 기사 하나인 읽히는 사건 넷, 지난 사건의 첫 기사부터 사건 C 의 기사까지
		StorylineView parent = this.directory.find(new StorylineId(this.wide)).orElseThrow();
		assertThat(parent.eventCount()).isEqualTo(5);
		assertThat(parent.articleCount()).isEqualTo(4);
		assertThat(parent.firstArticleAt()).isEqualTo(Instant.parse("2026-11-03T00:00:00Z"));
		assertThat(parent.lastArticleAt()).isEqualTo(Instant.parse("2026-11-05T05:00:00Z"));
		assertThat(parent.representativeEvent()).isEqualTo(new EventId(this.eventA));
		assertThat(parent.parent()).isNull();
		// 하위는 자기 직접 소속만 갖고 상위를 싣는다
		StorylineView child = this.directory.find(new StorylineId(this.narrow)).orElseThrow();
		assertThat(child.eventCount()).isEqualTo(2);
		assertThat(child.articleCount()).isEqualTo(1);
		assertThat(child.parent()).isEqualTo(new StorylineId(this.wide));
		assertThat(this.directory.events(new StorylineId(this.narrow))).extracting(StorylineEvent::direct)
			.containsExactly(true, true);
	}

	@Test
	void anEventIsInItsStorylineAndEveryAncestor() {
		// quiet ⊃ wide ⊃ narrow
		contains(this.wide, this.narrow);
		contains(this.quiet, this.wide);

		List<EventStoryline> ofC = this.directory.storylinesOf(new EventId(this.eventC));
		assertThat(ofC).extracting(found -> found.storyline().id().value())
			.containsExactly(this.wide, this.narrow, this.quiet);
		assertThat(ofC).extracting(EventStoryline::direct).containsExactly(false, true, false);
		assertThat(this.directory.storylinesOf(new EventId(this.eventA))).extracting(EventStoryline::direct)
			.containsExactly(true, false);

		// 상위에도 직접 든 사건은 직접이다 — 타임라인에는 한 번만 나온다
		member(this.wide, this.eventC);
		assertThat(this.directory.storylinesOf(new EventId(this.eventC))).extracting(EventStoryline::direct)
			.containsExactly(true, true, false);
		assertThat(this.directory.events(new StorylineId(this.wide)))
			.filteredOn(event -> event.event().id().value() == this.eventC)
			.extracting(StorylineEvent::direct)
			.containsExactly(true);
	}

	@Test
	void theTallyAndTheBigJudgementCountTheEventsOfTheChildren() {
		contains(this.wide, this.narrow);

		List<BigStoryline> big = this.directory.bigStorylines(PERIOD);

		// wide 는 사건 C 의 기사까지 센다 — 기사 다섯 · 소스 셋 · 중요도 합 17 · 보도 강도 17 ÷ 4 = 4.25. narrow 는 그대로 1.25 다
		assertThat(big).extracting(storyline -> storyline.storyline().id().value()).containsExactly(this.wide);
		EventTally tally = big.getFirst().tally();
		assertThat(tally.articles()).isEqualTo(5);
		assertThat(tally.sources()).isEqualTo(3);
		assertThat(tally.importance()).isEqualByComparingTo("4.25");
		assertThat(big.getFirst().intensity()).hasToString("4.25");
		// 기준이 하위에도 닿으면 상위와 하위가 함께 나온다 — 상위의 보도 강도는 하위보다 낮을 수 없어 앞선다
		List<BigStoryline> both = this.administration.preview(new BigStorylineCriteria(new BigDecimal("1.00")), PERIOD);
		assertThat(both).extracting(storyline -> storyline.storyline().id().value()).containsExactly(this.wide, this.narrow);
		assertThat(both.getLast().storyline().parent()).isEqualTo(new StorylineId(this.wide));
		// 사건 C 가 상위에도 직접 들어도 상위의 합은 한 번만 센다
		member(this.wide, this.eventC);
		assertThat(this.directory.bigStorylines(PERIOD).getFirst().intensity()).hasToString("4.25");
	}

	@Test
	void aParentWithNoEventOfItsOwnInThePeriodIsInTheListThroughItsChild() {
		// quiet = {지난 사건} 은 기간 안 기사가 없다 — 하위 wide 의 사건으로 기간의 스토리라인이 된다
		contains(this.quiet, this.wide);

		StorylinePage first = this.directory.list(PERIOD, null, 2);

		// 순서는 직접 소속의 마지막 기사 시각이다 — narrow(11-05T05) · wide(11-05T04) · quiet(11-01)
		assertThat(first.storylines()).extracting(summary -> summary.storyline().id().value())
			.containsExactly(this.narrow, this.wide);
		assertThat(first.storylines().getLast().storyline().parent()).isEqualTo(new StorylineId(this.quiet));
		assertThat(first.next())
			.isEqualTo(new StorylineCursor(Instant.parse("2026-11-05T04:00:00Z"), new StorylineId(this.wide)));
		StorylinePage second = this.directory.list(PERIOD, first.next(), 2);
		assertThat(second.storylines()).extracting(summary -> summary.storyline().id().value()).containsExactly(this.quiet);
		assertThat(second.next()).isNull();
		// 행의 값은 닫은 값이다 — 사건 넷, 마지막 기사는 사건 B 의 것이라 잠잠이 아니라 진행이고, 집계는 wide 와 같아 크다
		StorylineSummary row = second.storylines().getFirst();
		assertThat(row.storyline().eventCount()).isEqualTo(4);
		assertThat(row.storyline().lastArticleAt()).isEqualTo(Instant.parse("2026-11-05T04:00:00Z"));
		assertThat(row.storyline().state()).isEqualTo(StorylineState.ACTIVE);
		assertThat(row.tally().articles()).isEqualTo(4);
		assertThat(row.tally().sources()).isEqualTo(3);
		assertThat(row.big()).isTrue();
		// 상위와 하위가 함께 큰 스토리라인으로 나온다 — 집계가 같아 ID 순
		assertThat(this.directory.bigStorylines(PERIOD)).extracting(storyline -> storyline.storyline().id().value())
			.containsExactly(this.wide, this.quiet);
	}

	@Test
	void aParentWithNoReadableEventInItsClosureKeepsItsStoredArticleCountAndTimes() {
		// 상위 · 하위의 구성 사건이 모두 합쳐졌는데 아직 소속을 따르지 않았다 — 닫은 사건 집합에 읽히는 사건이 하나도 없다
		long alsoMerged = event("2026-11-05T00:40:00Z", "2026-11-05T00:40:00Z");
		StorylineRows.merge(this.jdbc, alsoMerged, this.eventA);
		long parent = storyline("2026-11-02T00:00:00Z", "2026-11-02T06:00:00Z", this.eventMerged);
		member(parent, this.eventMerged);
		long child = storyline("2026-11-04T00:00:00Z", "2026-11-04T00:00:00Z", alsoMerged);
		member(child, alsoMerged);
		contains(parent, child);

		StorylineView view = this.directory.find(new StorylineId(parent)).orElseThrow();

		// 사건 수는 닫은 사건 집합의 크기(둘)이고, 기사 수 · 시각은 저장된 표현의 값이다
		assertThat(view.eventCount()).isEqualTo(2);
		assertThat(view.articleCount()).isEqualTo(1);
		assertThat(view.firstArticleAt()).isEqualTo(Instant.parse("2026-11-02T00:00:00Z"));
		assertThat(view.lastArticleAt()).isEqualTo(Instant.parse("2026-11-02T06:00:00Z"));
		assertThat(this.directory.events(new StorylineId(parent))).isEmpty();
	}

	@Test
	void theRelationsOfAStorylineAreReadWithContainmentFirst() {
		contains(this.wide, this.narrow);
		long criteriaId = this.relations.registerCriteria(RELATION_CRITERIA, NOW);
		this.relations.link(new Relation(this.ended, this.wide, RelationKind.FOLLOWS), 0.8, 2, criteriaId,
				NOW.plusSeconds(60));
		long merged = storyline("2026-11-02T00:00:00Z", "2026-11-02T00:00:00Z", this.eventA);
		this.jdbc.sql("update storylines set merged_into = :into, merged_at = now() where id = :id")
			.param("into", this.wide)
			.param("id", merged)
			.update();
		StorylineRelationView contains = new StorylineRelationView(StorylineRelationKind.CONTAINS,
				new StorylineId(this.wide), new StorylineId(this.narrow), NOW);
		StorylineRelationView follows = new StorylineRelationView(StorylineRelationKind.FOLLOWS,
				new StorylineId(this.ended), new StorylineId(this.wide), NOW.plusSeconds(60));

		assertThat(this.directory.relationsOf(new StorylineId(this.wide))).containsExactly(contains, follows);
		assertThat(this.directory.relationsOf(new StorylineId(this.narrow))).containsExactly(contains);
		// 합쳐진 스토리라인은 남는 쪽의 관계다
		assertThat(this.directory.relationsOf(new StorylineId(merged))).containsExactly(contains, follows);
		assertThat(this.directory.relationsOf(new StorylineId(this.spanning))).isEmpty();
		assertThat(this.directory.relationsOf(new StorylineId(999_999_999L))).isEmpty();
	}

	@Test
	void unlinkedCountsEventsWithArticlesInThePeriodAndNoMembership() {
		// 기간 안 사건은 A(2) · B(2) · C(1) · 소속 없는 사건(4) — 소속 없는 것은 하나, 그 기사 넷
		assertThat(this.directory.unlinked(PERIOD)).isEqualTo(new UnlinkedEvents(1, 4));
		// 대조 — 기간 밖 기사만 있는 소속 없는 사건은 세지 않는다
		long outside = event("2026-11-03T00:00:00Z", "2026-11-03T00:00:00Z");
		assign(article(6, 1.0), outside, this.assignment, "2026-11-03T00:00:00Z");
		assertThat(this.directory.unlinked(PERIOD)).isEqualTo(new UnlinkedEvents(1, 4));
		// 대조 — 모두 소속이 있으면 0 이다
		member(this.narrow, this.eventLoose);
		assertThat(this.directory.unlinked(PERIOD)).isEqualTo(new UnlinkedEvents(0, 0));
	}

	@Test
	void aChangedCriterionAppliesToTheNextQueryAndThePreviewWritesNothing() {
		// wide 3.00 · narrow 1.25 — 기준 20.00 이면 아무것도 크지 않다
		this.administration.change(new BigDecimal("20.00"), ChangeActor.OPERATOR);

		assertThat(this.directory.bigStorylines(PERIOD)).isEmpty();
		assertThat(this.directory.list(PERIOD, null, 10).storylines()).extracting(StorylineSummary::big)
			.containsExactly(false, false);
		// 둘 다 큰 제안 기준 — 보도 강도가 높은 wide 가 narrow 보다 앞선다(마지막 기사 시각은 narrow 가 늦다)
		assertThat(this.administration.preview(new BigStorylineCriteria(new BigDecimal("1.00")), PERIOD))
			.extracting(big -> big.storyline().id().value())
			.containsExactly(this.wide, this.narrow);
		assertThat(this.administration.criteria()).isEqualTo(new BigStorylineCriteria(new BigDecimal("20.00")));
		// 처음 값 · 픽스처의 2.00 · 이 시험의 20.00
		assertThat(this.administration.changes()).hasSize(3);
	}

	private void clear() {
		StorylineRows.clear(this.jdbc);
		if (!this.evaluated.isEmpty()) {
			this.jdbc.sql("delete from article_evaluations where article_id = any(:ids)")
				.param("ids", this.evaluated.toArray(Long[]::new))
				.update();
			this.evaluated.clear();
		}
		BigStorylineCriteriaRows.removeAll(this.jdbc);
	}

	private long article(long source, @Nullable Double importance) {
		long article = EventArticles.insert(this.jdbc, "스토리라인 조회 시험 기사", null, source, 10 + source);
		if (importance != null) {
			this.jdbc.sql("""
					insert into article_evaluations (article_id, tag_set_id, judgement_id, format_key, format_probability,
					    format_distribution, importance, importance_distribution, evaluated_at)
					values (:article, (select id from tag_sets order by number desc limit 1), :article, 'straight', 1.0,
					    '{}', :importance, '{}', '2026-11-05T00:00:00Z')
					""").param("article", article).param("importance", importance).update();
			this.evaluated.add(article);
		}
		return article;
	}

	private long event(String first, String last) {
		return StorylineRows.event(this.jdbc, "OPEN", "model-a", "[1,0]", 1, first, last, Map.of());
	}

	private long assignmentCriteria() {
		return this.jdbc.sql("""
				insert into event_assignment_criteria (window_seconds, upper, lower, store, probability, created_at)
				values (259200, 0.8, 0.55, 0.4, 0.5, now()) returning id
				""").query(Long.class).single();
	}

	private void assign(long article, long event, long criteriaId, String published) {
		this.jdbc.sql("""
				insert into event_articles (article_id, event_id, published_at, score, decided_by, opened, criteria_id,
				                            assigned_at)
				values (:article, :event, :published, 0.9, 'FORMULA', false, :criteria, '2026-11-06T00:00:00Z')
				""")
			.param("article", article)
			.param("event", event)
			.param("published", Instant.parse(published).atOffset(ZoneOffset.UTC))
			.param("criteria", criteriaId)
			.update();
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

	/** 포함 관계를 심는다 — parent ⊃ child */
	private void contains(long parent, long child) {
		long criteriaId = this.relations.registerCriteria(RELATION_CRITERIA, NOW);
		this.relations.link(new Relation(parent, child, RelationKind.CONTAINS), 0.7, 1, criteriaId, NOW);
	}

	private void member(long storyline, long event) {
		this.jdbc.sql("""
				insert into storyline_members (storyline_id, event_id, linked_by, linked_at)
				values (:storyline, :event, 'MERGE', '2026-11-06T00:00:00Z')
				""").param("storyline", storyline).param("event", event).update();
	}

}
