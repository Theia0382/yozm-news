package net.yozmnews.report.application;

import static net.yozmnews.report.ReportFixtures.event;
import static net.yozmnews.report.ReportFixtures.storyline;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.feedback.FeedbackRows;
import net.yozmnews.platform.UserId;
import net.yozmnews.reader.ReaderRows;
import net.yozmnews.report.ReportDirectory;
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPage;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.ReportSectionStats;
import net.yozmnews.report.ReportSectionView;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.ReportSummary;
import net.yozmnews.report.ReportTrend;
import net.yozmnews.report.ReportView;
import net.yozmnews.report.domain.view.ReportViewRepository;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 리포트 조회 — 사용자 범위, 목록 순서 · 쪽, 한 편의 칸과 스냅숏, 합쳐진 사건 · 스토리라인 풀기와 지운 것 빼기, 인용을 모두 잃은 섹션 숨기기.
 * <p>
 * 지키는 규칙: report-R11 · report-R12 · report-R25 · report-R26 · report-R18(생성 때 이은 직전 리포트)
 * <p>
 * 리포트 · 사건 · 스토리라인 행은 SQL 로 넣는다. 풀기는 실제 {@code event} · {@code storyline} 공개 조회를 거친다 — 합친 것은
 * {@code merged_into} 행으로, 지운 것은 행이 없는 ID 로. 기대값은 손으로 쓴 리터럴이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ReportDirectoryServiceTests {

	/** 이 시험에서 기본 기간으로 넣은 리포트 수 — 넣을 때마다 한 주씩 앞선다 */
	private int defaultReports;

	private static final UserId OWNER = new UserId(7);

	private static final UserId OTHER = new UserId(8);

	/** 시작한 리포트의 스냅숏 — 구독 주제 보도량 하나(report-R19 의 꼴, 손으로 씀) */
	private static final String SNAPSHOT = """
			{"version":2,"tagSet":4,
			 "period":{"kind":"WEEK","start":"2026-11-16T00:00:00Z","end":"2026-11-23T00:00:00Z"},
			 "coverage":{"basis":"FIRST_EVALUATED","topics":[
			   {"key":"economy.finance","name":"금융","articles":12,"previous":7,"trend":"ROSE"}]}}""";

	@Autowired
	ReportDirectory directory;

	@Autowired
	ReportViewRepository views;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	EventDirectory events;

	@Autowired
	StorylineDirectory storylines;

	@Autowired
	PlatformTransactionManager transactions;

	private ReaderRows rows;

	@BeforeEach
	void clear() {
		this.jdbc.sql("delete from reports").update();
		this.rows = new ReaderRows(this.jdbc);
		this.rows.clear();
	}

	@Test
	void listsTheUsersReportsByDescendingIdInPagesAndNeverAnotherUsers() {
		long first = report(OWNER, "READY");
		report(OTHER, "READY");
		long second = report(OWNER, "EMPTY");
		long third = report(OWNER, "SCHEDULED");

		ReportPage page = this.directory.list(OWNER, ReportPeriodKind.WEEK, null, 2);
		assertThat(page.reports()).extracting(ReportSummary::id)
			.containsExactly(new ReportId(third), new ReportId(second));
		assertThat(page.next()).isEqualTo(new ReportId(second));
		ReportPage rest = this.directory.list(OWNER, ReportPeriodKind.WEEK, page.next(), 2);
		assertThat(rest.reports()).extracting(ReportSummary::id).containsExactly(new ReportId(first));
		assertThat(rest.next()).isNull();
		assertThat(this.directory.list(OTHER, ReportPeriodKind.WEEK, null, 50).reports()).hasSize(1);
	}

	@Test
	void listsOnlyTheKindAskedAndPagesWithinIt() {
		long firstWeek = report(OWNER, "READY");
		long month = report(OWNER, "MONTH", "READY", Instant.parse("2026-10-01T00:00:00Z"),
				Instant.parse("2026-11-01T00:00:00Z"), "Asia/Seoul", SNAPSHOT);
		long secondWeek = report(OWNER, "EMPTY");

		ReportPage weeks = this.directory.list(OWNER, ReportPeriodKind.WEEK, null, 1);
		assertThat(weeks.reports()).extracting(ReportSummary::id).containsExactly(new ReportId(secondWeek));
		assertThat(this.directory.list(OWNER, ReportPeriodKind.WEEK, weeks.next(), 1).reports())
			.extracting(ReportSummary::id)
			.containsExactly(new ReportId(firstWeek));
		assertThat(this.directory.list(OWNER, ReportPeriodKind.MONTH, null, 50).reports()).extracting(ReportSummary::id)
			.containsExactly(new ReportId(month));
	}

	@Test
	void carriesThePreviousReportItRecordedAndNothingOnceThatRowIsGone() {
		long previous = report(OWNER, "READY");
		long current = report(OWNER, "READY");
		this.jdbc.sql("update reports set previous_report_id = :previous where id = :id")
			.param("previous", previous)
			.param("id", current)
			.update();

		ReportView view = this.directory.find(OWNER, new ReportId(current)).orElseThrow();
		assertThat(view.previous()).extracting(ReportSummary::id).isEqualTo(new ReportId(previous));
		assertThat(view.previous().firstDay()).isEqualTo(LocalDate.parse("2026-11-16"));

		this.jdbc.sql("delete from reports where id = :id").param("id", previous).update();
		assertThat(this.jdbc.sql("select previous_report_id is null from reports where id = :id")
			.param("id", current)
			.query(Boolean.class)
			.single()).isTrue();
		assertThat(this.directory.find(OWNER, new ReportId(current)).orElseThrow().previous()).isNull();

		// 직전 리포트 칸이 다른 사용자의 행을 가리켜도 내지 않는다 — 직전 행도 같은 사용자 조건으로 읽는다
		long others = report(OTHER, "READY");
		this.jdbc.sql("update reports set previous_report_id = :previous where id = :id")
			.param("previous", others)
			.param("id", current)
			.update();
		assertThat(this.directory.find(OWNER, new ReportId(current)).orElseThrow().previous()).isNull();
	}

	@Test
	void writesThePeriodDaysInTheRowZoneAndBeforeStartInTheUsersCurrentZone() {
		// 서울 자정에 시작하는 주 — UTC 로는 전날 15 시
		Instant start = Instant.parse("2026-11-23T15:00:00Z");
		Instant end = Instant.parse("2026-11-30T15:00:00Z");
		// 같은 사용자 · 종류 · 기간은 하나라 시작을 1분씩 늦춘다 — 날짜는 그대로다
		long seoul = report(OWNER, "WEEK", "EMPTY", start, end, "Asia/Seoul", SNAPSHOT);
		long utc = report(OWNER, "WEEK", "EMPTY", start.plusSeconds(60), end, "UTC", SNAPSHOT);
		long scheduled = report(OWNER, "WEEK", "SCHEDULED", start.plusSeconds(120), end, "Asia/Seoul", SNAPSHOT);
		ReportDirectory inSeoul = new ReportDirectoryService(this.views, this.events, this.storylines,
				user -> Optional.of(ZoneId.of("Asia/Seoul")), this.transactions);

		assertThat(this.directory.find(OWNER, new ReportId(seoul)).orElseThrow().summary().firstDay())
			.isEqualTo(LocalDate.parse("2026-11-24"));
		assertThat(this.directory.find(OWNER, new ReportId(seoul)).orElseThrow().summary().lastDay())
			.isEqualTo(LocalDate.parse("2026-11-30"));
		assertThat(this.directory.find(OWNER, new ReportId(utc)).orElseThrow().summary().firstDay())
			.isEqualTo(LocalDate.parse("2026-11-23"));
		assertThat(inSeoul.find(OWNER, new ReportId(scheduled)).orElseThrow().summary().firstDay())
			.isEqualTo(LocalDate.parse("2026-11-24"));
		assertThat(inSeoul.list(OWNER, ReportPeriodKind.WEEK, null, 50).reports()).extracting(ReportSummary::firstDay)
			.containsExactly(LocalDate.parse("2026-11-24"), LocalDate.parse("2026-11-23"), LocalDate.parse("2026-11-24"));
		// 시간대 조회가 빈 값(계정 없음)인 사용자의 시작 전 행은 UTC 로 적는다 — 계정 행은 같은 문맥을 쓰는 다른 시험이 남길 수 있어
		// 빈 값을 내는 조회를 직접 만든다
		ReportDirectory withoutAccount = new ReportDirectoryService(this.views, this.events, this.storylines,
				user -> Optional.empty(), this.transactions);
		assertThat(withoutAccount.find(OWNER, new ReportId(scheduled)).orElseThrow().summary().firstDay())
			.isEqualTo(LocalDate.parse("2026-11-23"));
	}

	@Test
	void refusesAPageSizeOutsideOneToFifty() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.list(OWNER, ReportPeriodKind.WEEK, null, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.list(OWNER, ReportPeriodKind.WEEK, null, 51));
		assertThat(this.directory.list(OWNER, ReportPeriodKind.WEEK, null, 1).reports()).isEmpty();
		assertThat(this.directory.list(OWNER, ReportPeriodKind.WEEK, null, 50).reports()).isEmpty();
	}

	@Test
	void findsTheOwnersReportWithItsFieldsAndNothingForAnotherUser() {
		long id = report(OWNER, "READY");
		section(id, "OVERVIEW", 0, null, "개요");
		section(id, "COVERAGE", 1, "보도량", "본문");

		ReportView view = this.directory.find(OWNER, new ReportId(id)).orElseThrow();

		assertThat(view.summary()).isEqualTo(new ReportSummary(new ReportId(id), ReportPeriodKind.WEEK,
				Instant.parse("2026-11-16T00:00:00Z"), Instant.parse("2026-11-23T00:00:00Z"), LocalDate.parse("2026-11-16"),
				LocalDate.parse("2026-11-23"), ReportStatus.READY, null, Instant.parse("2026-11-23T00:30:00Z"),
				Instant.parse("2026-11-23T00:31:00Z")));
		assertThat(view.previous()).isNull();
		assertThat(view.provider()).isEqualTo("openai");
		assertThat(view.model()).isEqualTo("gpt-5.5-mini");
		assertThat(view.inputTokens()).isEqualTo(120);
		assertThat(view.outputTokens()).isEqualTo(45);
		assertThat(view.hiddenSections()).isZero();
		assertThat(view.sections()).containsExactly(
				new ReportSectionView(ReportSectionKind.OVERVIEW, null, "개요", List.of(), null),
				new ReportSectionView(ReportSectionKind.COVERAGE, "보도량", "본문", List.of(), new ReportSectionStats.Coverage(
						List.of(new ReportSectionStats.TopicCount("금융", 12, 7L, ReportTrend.ROSE)))));
		assertThat(this.directory.find(OTHER, new ReportId(id))).isEmpty();
		assertThat(this.directory.find(OWNER, new ReportId(id + 1_000))).isEmpty();
		assertThat(this.views.sections(OWNER, new ReportId(id))).hasSize(2);
		assertThat(this.views.sections(OTHER, new ReportId(id))).isEmpty();
		assertThat(this.views.page(OTHER, ReportPeriodKind.WEEK, null, 10)).isEmpty();
	}

	@Test
	void aFailedReportShowsItsCodeAndNoSections() {
		long id = this.jdbc.sql("""
				insert into reports (user_id, period_kind, period_start, period_end, status, provider, model, stats_snapshot,
				                     error_code, error_message, created_at, started_at, completed_at, zone, dispatch_token,
				                     dispatched_at, scheduled_at, regenerations, version)
				values (7, 'MONTH', timestamptz '2026-10-01 00:00Z', timestamptz '2026-11-01 00:00Z', 'FAILED', 'openai',
				        'gpt-5.5-mini', cast('{}' as jsonb), 'KEY_MISSING', '쓸 모델 공급자의 저장한 키가 없다', timestamptz '2026-11-01 01:00Z',
				        timestamptz '2026-11-01 01:00Z', timestamptz '2026-11-01 01:00Z', 'Asia/Seoul', gen_random_uuid(),
				        timestamptz '2026-11-01 01:00Z', timestamptz '2026-11-01 01:00Z', 0, 1)
				returning id""").query(Long.class).single();
		section(id, "OVERVIEW", 0, null, "남은 개요");

		ReportView view = this.directory.find(OWNER, new ReportId(id)).orElseThrow();

		assertThat(view.summary().failure()).isEqualTo(ReportFailure.KEY_MISSING);
		assertThat(view.summary().periodKind()).isEqualTo(ReportPeriodKind.MONTH);
		assertThat(view.inputTokens()).isNull();
		assertThat(view.sections()).isEmpty();
	}

	@Test
	void resolvesMergedTargetsThroughTheRealQueriesAndLeavesOutRemovedOnes() {
		long kept = FeedbackRows.event(this.jdbc);
		long absorbed = FeedbackRows.event(this.jdbc);
		long survivor = FeedbackRows.event(this.jdbc);
		FeedbackRows.merge(this.jdbc, absorbed, survivor);
		long removed = FeedbackRows.event(this.jdbc);
		FeedbackRows.deleteEvent(this.jdbc, removed);
		long line = storylineOf(kept);
		long mergedLine = storylineOf(survivor);
		long survivingLine = storylineOf(survivor);
		this.rows.merge(mergedLine, survivingLine);
		long id = report(OWNER, "READY");
		section(id, "OVERVIEW", 0, null, "개요");
		long sectionId = section(id, "STORYLINES", 1, "흐름", "본문");
		item(sectionId, 0, null, absorbed);
		item(sectionId, 1, null, removed);
		item(sectionId, 2, mergedLine, null);
		item(sectionId, 3, null, survivor);
		item(sectionId, 4, line, null);
		item(sectionId, 5, 999_999L, null);
		item(sectionId, 6, null, kept);

		List<ReportSectionView> sections = this.directory.find(OWNER, new ReportId(id)).orElseThrow().sections();

		assertThat(sections).hasSize(2);
		assertThat(sections.get(1).citations()).containsExactly(event(survivor), storyline(survivingLine), storyline(line),
				event(kept));
	}

	@Test
	void resolvesTheUserStorylineHeadsOfTheSnapshotThroughTheRealQueriesLikeTheCitations() {
		long line = storylineOf(FeedbackRows.event(this.jdbc));
		long mergedLine = storylineOf(FeedbackRows.event(this.jdbc));
		long survivingLine = storylineOf(FeedbackRows.event(this.jdbc));
		this.rows.merge(mergedLine, survivingLine);
		long id = report(OWNER, "READY", """
				{"version":2,"tagSet":4,
				 "period":{"kind":"WEEK","start":"2026-11-16T00:00:00Z","end":"2026-11-23T00:00:00Z"},
				 "storylines":{"basis":"PUBLISHED","userStorylines":[
				   {"head":%d,"tops":[%d],"articles":8,"previous":2,"trend":"ROSE","trackedInPeriod":false},
				   {"head":999997,"tops":[999997],"articles":5,"previous":null,"trend":"NO_COMPARISON","trackedInPeriod":false},
				   {"head":%d,"tops":[%d],"articles":3,"previous":3,"trend":"STEADY","trackedInPeriod":true}]}}"""
			.formatted(mergedLine, mergedLine, line, line));
		section(id, "OVERVIEW", 0, null, "개요");
		item(section(id, "STORYLINES", 1, "흐름", "본문"), 0, line, null);

		ReportView view = this.directory.find(OWNER, new ReportId(id)).orElseThrow();

		assertThat(view.sections().get(1).stats()).isEqualTo(new ReportSectionStats.UserStorylines(List.of(
				new ReportSectionStats.UserStorylineCount(new StorylineId(survivingLine), 8, 2L, ReportTrend.ROSE, false),
				new ReportSectionStats.UserStorylineCount(new StorylineId(line), 3, 3L, ReportTrend.STEADY, true))));
	}

	@Test
	void hidesASectionThatLostEveryCitationAndTheOverviewWhenEveryKindSectionIsHidden() {
		long removed = FeedbackRows.event(this.jdbc);
		FeedbackRows.deleteEvent(this.jdbc, removed);
		long kept = FeedbackRows.event(this.jdbc);
		long partly = report(OWNER, "READY");
		section(partly, "OVERVIEW", 0, null, "개요");
		item(section(partly, "STORYLINES", 1, "흐름", "본문"), 0, null, removed);
		section(partly, "INTERESTS", 2, "관심", "본문");
		item(section(partly, "COVERAGE", 3, "보도량", "본문"), 0, null, kept);
		long whole = report(OWNER, "READY");
		section(whole, "OVERVIEW", 0, null, "개요");
		item(section(whole, "STORYLINES", 1, "흐름", "본문"), 0, 999_998L, null);

		assertThat(this.directory.find(OWNER, new ReportId(partly)).orElseThrow().sections())
			.extracting(ReportSectionView::kind)
			.containsExactly(ReportSectionKind.OVERVIEW, ReportSectionKind.INTERESTS, ReportSectionKind.COVERAGE);
		assertThat(this.directory.find(OWNER, new ReportId(whole)).orElseThrow().sections()).isEmpty();
		assertThat(this.jdbc.sql("select count(*) from report_sections where report_id = :id")
			.param("id", whole)
			.query(Long.class)
			.single()).isEqualTo(2L);
	}

	@Test
	void resolvesMoreCitedEventsThanOneResolveCallTakes() {
		List<Long> cited = this.jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at, last_joined_at)
				select 'OPEN', 'model-a', cast('[1,0]' as halfvec), 1, now(), now(), now(), now() from generate_series(1, :n)
				returning id""").param("n", EventDirectory.MAX_RESOLVE_EVENTS + 1).query(Long.class).list()
			.stream()
			.sorted()
			.toList();
		long survivor = FeedbackRows.event(this.jdbc);
		FeedbackRows.merge(this.jdbc, cited.getLast(), survivor);
		long id = report(OWNER, "READY");
		section(id, "OVERVIEW", 0, null, "개요");
		long sectionId = section(id, "STORYLINES", 1, "흐름", "본문");
		this.jdbc.sql("""
				insert into report_items (report_section_id, position, storyline_id, event_id)
				select :section, (row_number() over (order by id)) - 1, null, id from events where id between :from and :to""")
			.param("section", sectionId)
			.param("from", cited.getFirst())
			.param("to", cited.getLast())
			.update();

		List<ReportSectionView> sections = this.directory.find(OWNER, new ReportId(id)).orElseThrow().sections();

		assertThat(sections).hasSize(2);
		assertThat(sections.get(1).citations()).hasSize(cited.size())
			.startsWith(event(cited.getFirst()))
			.endsWith(event(survivor));
	}

	private long report(UserId user, String status) {
		return report(user, status, SNAPSHOT);
	}

	/**
	 * 주간 리포트 — 첫 행은 2026-11-16 ~ 11-23 이고 부를 때마다 한 주씩 앞선다(같은 사용자 · 종류 · 기간은 하나 — 유일 인덱스)
	 */
	private long report(UserId user, String status, String snapshot) {
		Duration earlier = Duration.ofDays(7L * this.defaultReports++);
		return report(user, "WEEK", status, Instant.parse("2026-11-16T00:00:00Z").minus(earlier),
				Instant.parse("2026-11-23T00:00:00Z").minus(earlier), "Asia/Seoul", snapshot);
	}

	/** 리포트 행 — 시작한 행은 공급자 · 모델 · 스냅숏 · 시간대를 함께 넣는다(테이블 제약) */
	private long report(UserId user, String kind, String status, Instant start, Instant end, String zone,
			String snapshot) {
		boolean started = !status.equals("SCHEDULED");
		boolean finished = status.equals("READY") || status.equals("EMPTY");
		return this.jdbc.sql("""
				insert into reports (user_id, period_kind, period_start, period_end, status, provider, model, input_tokens,
				                     output_tokens, stats_snapshot, created_at, started_at, completed_at, zone, dispatch_token,
				                     dispatched_at, scheduled_at, regenerations, version)
				values (:user, :kind, :start, :end, :status,
				        :provider, :model, :input, :output, cast(:snapshot as jsonb), timestamptz '2026-11-23 00:30Z',
				        :started, :completed, :zone, gen_random_uuid(), timestamptz '2026-11-23 00:30Z',
				        timestamptz '2026-11-23 00:30Z', 0, 1)
				returning id""")
			.param("user", user.value())
			.param("kind", kind)
			.param("start", start.atOffset(java.time.ZoneOffset.UTC))
			.param("end", end.atOffset(java.time.ZoneOffset.UTC))
			.param("status", status)
			.param("provider", started ? "openai" : null, java.sql.Types.VARCHAR)
			.param("model", started ? "gpt-5.5-mini" : null, java.sql.Types.VARCHAR)
			.param("input", status.equals("READY") ? 120 : null, java.sql.Types.INTEGER)
			.param("output", status.equals("READY") ? 45 : null, java.sql.Types.INTEGER)
			.param("snapshot", started ? snapshot : null, java.sql.Types.VARCHAR)
			.param("started", started ? Instant.parse("2026-11-23T00:30:00Z").atOffset(java.time.ZoneOffset.UTC) : null,
					java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
			.param("completed", finished ? Instant.parse("2026-11-23T00:31:00Z").atOffset(java.time.ZoneOffset.UTC) : null,
					java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
			.param("zone", started ? zone : null, java.sql.Types.VARCHAR)
			.query(Long.class)
			.single();
	}

	private long section(long report, String kind, int position, String title, String body) {
		return this.jdbc.sql("""
				insert into report_sections (report_id, kind, position, title, body)
				values (:report, :kind, :position, :title, :body) returning id""")
			.param("report", report)
			.param("kind", kind)
			.param("position", position)
			.param("title", title, java.sql.Types.VARCHAR)
			.param("body", body)
			.query(Long.class)
			.single();
	}

	private void item(long section, int position, Long storylineId, Long eventId) {
		this.jdbc.sql("""
				insert into report_items (report_section_id, position, storyline_id, event_id)
				values (:section, :position, :storyline, :event)""")
			.param("section", section)
			.param("position", position)
			.param("storyline", storylineId, java.sql.Types.BIGINT)
			.param("event", eventId, java.sql.Types.BIGINT)
			.update();
	}

	private long storylineOf(long member) {
		long id = this.rows.storyline("2026-11-17T00:00:00Z", "2026-11-18T00:00:00Z", member);
		this.rows.member(id, member);
		return id;
	}

}
