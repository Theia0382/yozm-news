package net.yozmnews.report.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestClocks;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.platform.UserId;
import net.yozmnews.reader.InterestRows;
import net.yozmnews.reader.ReaderRows;
import net.yozmnews.reader.ReportRows;
import net.yozmnews.reader.UserStorylineSummaryRows;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.adapter.out.fake.FakeReportGenerator;
import net.yozmnews.report.domain.content.ReportSectionRepository;
import net.yozmnews.report.domain.generator.ReportGenerator;
import net.yozmnews.report.domain.generator.ReportOutcome;
import net.yozmnews.report.domain.generator.ReportRequest;
import net.yozmnews.report.domain.report.ReportLedger;
import net.yozmnews.report.domain.report.ReportPeriod;
import net.yozmnews.report.domain.report.ReportRepository;
import net.yozmnews.tag.TagSetActivationResult;
import net.yozmnews.tag.TagSetAdministration;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.application.TagSetRows;
import net.yozmnews.user.ReportModelDirectory;
import net.yozmnews.user.UserStorylineFixtures;
import net.yozmnews.user.UserTables;
import net.yozmnews.user.UserTimezones;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 생성 작업을 실제 모듈로 끝까지 — 예약한 리포트 하나를 생성 작업({@link ReportRun})이 실제 재료 조립으로 모아 생성하고, 시작할 때 행에 남긴
 * 스냅숏 · 직전 리포트와 생성기가 받은 사용자 구획을 본다. {@code user} 의 관심 설정 · 추적한 사용자 스토리라인 · 정리, {@code digest} 의 관심사 히스토리,
 * {@code tag} 의 보도량 · 계보, {@code storyline} · {@code event} · {@code article} 의 조회 모두 실제 빈이고 행은 시험이 SQL 로 넣는다.
 * 생성기는 받은 요청을 기록하는 로컬 대체물이다. 기대값은 손으로 쓴 리터럴이다.
 * <p>
 * 지키는 규칙: report-R14 ~ report-R21 의 끝까지 길, 월간 입력(report-R28 · report-R29 — 기간 안에 다 든 READY 주간만), 같은 리포트의
 * 생성은 한 실행(report-R13)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ReportRunEndToEndTests {

	private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");

	/** 서울의 2026-11-16(월) 0 시 ~ 11-23 0 시 */
	private static final ReportPeriod WEEK = new ReportPeriod(ReportPeriodKind.WEEK, ReportRows.WEEK_START,
			ReportRows.WEEK_END);

	/** 직전 주간 — 끝이 이번 시작 */
	private static final ReportPeriod PREVIOUS = new ReportPeriod(ReportPeriodKind.WEEK,
			Instant.parse("2026-11-08T15:00:00Z"), ReportRows.WEEK_START);

	/** 서울의 2026-10-19 0 시 ~ 11-19 0 시 */
	private static final ReportPeriod MONTH = new ReportPeriod(ReportPeriodKind.MONTH,
			Instant.parse("2026-10-18T15:00:00Z"), Instant.parse("2026-11-18T15:00:00Z"));

	private static final String FINANCE = "economy.finance";

	private static final String BANKING = "economy.banking";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	ReportRepository reports;

	@Autowired
	ReportSectionRepository sections;

	@Autowired
	ReportLedger ledger;

	@Autowired
	ReportModelDirectory models;

	@Autowired
	UserTimezones timezones;

	@Autowired
	ReportMaterialCollection collection;

	@Autowired
	ReportEndings endings;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	TagSetAdministration administration;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private ReaderRows rows;

	private InterestRows interests;

	private ReportRows reportRows;

	private final List<ReportRequest> requests = Collections.synchronizedList(new ArrayList<>());

	@BeforeEach
	void prepare() {
		this.rows = new ReaderRows(this.jdbc);
		this.interests = new InterestRows(this.jdbc);
		this.reportRows = new ReportRows(this.jdbc);
		clear();
	}

	@AfterEach
	void clear() {
		this.requests.clear();
		this.reportRows.clear();
		this.interests.clear();
		this.rows.clear();
		this.jdbc.sql("delete from llm_credentials where user_id in (select id from users where email like 'report-e2e-%')")
			.update();
		UserTables.deleteByEmail(this.jdbc, "report-e2e-%");
		awaitPublications();
		TagSetRows.removeVersionsAfterFirst(this.jdbc);
		TagSetRows.resetThresholds(this.jdbc);
	}

	@Test
	void aWeeklyReportCollectsItsMaterialFromTheRealModulesAndCarriesThePreviousValues() {
		TagSetId active = this.tagSets.activeTagSet().id();
		UserId user = user("report-e2e-weekly@example.com", FINANCE);
		// 구독 주제 — 기간 안 첫 평가 둘, 기간 앞 첫 평가 하나(세지 않는다)
		evaluated("금융 기사 하나", active, FINANCE, "2026-11-17T00:00:00Z");
		evaluated("금융 기사 둘", active, FINANCE, "2026-11-20T00:00:00Z");
		evaluated("지난 금융 기사", active, FINANCE, "2026-11-10T00:00:00Z");
		// 추적한 사용자 스토리라인 — 사건 둘(기간 앞 기사 하나 · 기간 안 기사 하나), 기간 안에 추적을 더했다, 정리가 있다
		long before = event("지난 금리 동결", "2026-11-10T01:00:00Z");
		long inside = event("금리 인상 발표", "2026-11-17T01:00:00Z");
		long storyline = this.rows.storyline("2026-11-10T01:00:00Z", "2026-11-17T01:00:00Z", inside);
		this.rows.member(storyline, before);
		this.rows.member(storyline, inside);
		long userStoryline = UserStorylineFixtures.tracked(this.jdbc, user, storyline, Instant.parse("2026-11-18T00:00:00Z"));
		new UserStorylineSummaryRows(this.jdbc).summarize(userStoryline, "금리", Instant.parse("2026-11-19T00:00:00Z"));
		// 직전 주간 — 같은 버전 스냅숏(사용자 스토리라인 기사 5 · 주제 기사 1)과 보이는 글
		long previous = this.reportRows.report(user.value(), "WEEK", "READY", PREVIOUS.start(), PREVIOUS.end(), """
				{"version": 2, "tagSet": %d,
				 "period": {"kind": "WEEK", "start": "2026-11-08T15:00:00Z", "end": "2026-11-15T15:00:00Z"},
				 "storylines": {"basis": "PUBLISHED", "userStorylines": [{"head": %d, "tops": [%d], "articles": 5, "previous": null,
				   "trend": "NO_COMPARISON", "trackedInPeriod": false}]},
				 "coverage": {"basis": "FIRST_EVALUATED", "topics": [{"key": "economy.finance", "name": "금융",
				   "articles": 1, "previous": null, "trend": "NO_COMPARISON"}]}}"""
			.formatted(active.value(), storyline, storyline), null);
		this.reportRows.section(previous, "OVERVIEW", 0, null, "지난주 금리는 움직이지 않았다.");
		ReportId id = schedule(user, WEEK);

		assertThat(run().run(id)).isEqualTo(ReportStatus.READY);

		Map<String, Object> row = row(id);
		assertThat(row).containsEntry("previous_report_id", previous).containsEntry("zone", "Asia/Seoul");
		// 사용자 스토리라인 — 기간 안 기사 1, 직전 5 → 차이 4 · 절반 이하라 줄었다, 기간 안 추적. 주제 — 기간 안 첫 평가 2, 직전 1 → 차이가 3 보다
		// 작아 그대로(report-R15)
		JsonNode snapshot = JSON.readTree((String) row.get("stats_snapshot"));
		assertThat(without(snapshot, "interests")).isEqualTo(JSON.readTree("""
				{"version": 2, "tagSet": %d,
				 "period": {"kind": "WEEK", "start": "2026-11-15T15:00:00Z", "end": "2026-11-22T15:00:00Z"},
				 "storylines": {"basis": "PUBLISHED", "userStorylines": [{"head": %d, "tops": [%d], "articles": 1, "previous": 5,
				   "trend": "FELL", "trackedInPeriod": true}]},
				 "coverage": {"basis": "FIRST_EVALUATED", "topics": [{"key": "economy.finance", "name": "금융",
				   "articles": 2, "previous": 1, "trend": "STEADY"}]}}""".formatted(active.value(), storyline, storyline)));
		// 관심 — 실제 관심사 히스토리의 두 점. 값은 관심도 산식의 몫이라 여기서는 줄의 키와 변화만 본다: 구독 주제는 기간 앞부터 있어 두 점에
		// 다 있고, 추적한 사용자 스토리라인은 기간 안에 추적해 시작 점에 없다. 둘 다 관심 있음 기준을 넘지 않아 변화가 없다
		JsonNode interestsOf = snapshot.get("interests");
		assertThat(interestsOf.get("topics")).hasSize(1);
		assertThat(interestsOf.get("topics").get(0).get("key").asString()).isEqualTo("economy.finance");
		assertThat(interestsOf.get("topics").get(0).get("start").isNumber()).isTrue();
		assertThat(interestsOf.get("topics").get(0).get("shift").asString()).isEqualTo("NONE");
		assertThat(interestsOf.get("userStorylines")).hasSize(1);
		assertThat(interestsOf.get("userStorylines").get(0).get("head").asLong()).isEqualTo(storyline);
		assertThat(interestsOf.get("userStorylines").get(0).get("start").isNull()).isTrue();
		assertThat(interestsOf.get("userStorylines").get(0).get("end").asDouble()).isPositive();
		assertThat(interestsOf.get("userStorylines").get(0).get("shift").asString()).isEqualTo("NONE");
		// 생성기가 받은 사용자 구획 — 사용자 스토리라인의 정리 · 기간 안 사건의 대표 기사 제목 · 직전 리포트의 글
		assertThat(this.requests).hasSize(1);
		String content = this.requests.getFirst().prompt().userContent();
		assertThat(content).contains("금리 제목", "금리 요약", "금리 인상 발표", "지난주 금리는 움직이지 않았다.")
			.doesNotContain("지난 금리 동결");
	}

	@Test
	void aPreviousSnapshotOfAnOlderTagSetVersionIsMatchedThroughTheLineage() {
		TagSetId first = this.tagSets.activeTagSet().id();
		TagSetId second = activateRenamedFinance(first);
		UserId user = user("report-e2e-lineage@example.com", BANKING);
		evaluated("은행 기사", second, BANKING, "2026-11-17T00:00:00Z");
		// 직전 스냅숏은 버전 1 의 finance 4 — 계보(finance → banking)로 이어야 직전 값이 있다
		this.reportRows.report(user.value(), "WEEK", "READY", PREVIOUS.start(), PREVIOUS.end(), """
				{"version": 2, "tagSet": %d,
				 "period": {"kind": "WEEK", "start": "2026-11-08T15:00:00Z", "end": "2026-11-15T15:00:00Z"},
				 "coverage": {"basis": "FIRST_EVALUATED", "topics": [{"key": "economy.finance", "name": "금융",
				   "articles": 4, "previous": null, "trend": "NO_COMPARISON"}]}}""".formatted(first.value()), null);
		ReportId id = schedule(user, WEEK);

		assertThat(run().run(id)).isEqualTo(ReportStatus.READY);

		assertThat(without(JSON.readTree((String) row(id).get("stats_snapshot")), "interests")).isEqualTo(JSON.readTree("""
				{"version": 2, "tagSet": %d,
				 "period": {"kind": "WEEK", "start": "2026-11-15T15:00:00Z", "end": "2026-11-22T15:00:00Z"},
				 "coverage": {"basis": "FIRST_EVALUATED", "topics": [{"key": "economy.banking", "name": "금융",
				   "articles": 1, "previous": 4, "trend": "FELL"}]}}""".formatted(second.value())));
	}

	@Test
	void aMonthlyReportCarriesOnlyTheReadyWeekliesWhollyInsideItsPeriod() {
		UserId user = user("report-e2e-monthly@example.com", FINANCE);
		UserId other = user("report-e2e-monthly-other@example.com", FINANCE);
		weekly(user, "READY", "2026-10-18T15:00:00Z", "2026-10-25T15:00:00Z", "안의 첫 주 개요");
		weekly(user, "READY", "2026-10-25T15:00:00Z", "2026-11-01T15:00:00Z", "안의 둘째 주 개요");
		weekly(user, "FAILED", "2026-11-01T15:00:00Z", "2026-11-08T15:00:00Z", "실패한 주 개요");
		weekly(user, "READY", "2026-10-14T15:00:00Z", "2026-10-21T15:00:00Z", "시작을 걸친 주 개요");
		weekly(user, "READY", "2026-11-15T15:00:00Z", "2026-11-22T15:00:00Z", "끝을 걸친 주 개요");
		weekly(other, "READY", "2026-11-08T15:00:00Z", "2026-11-15T15:00:00Z", "다른 사용자의 주 개요");
		ReportId id = schedule(user, MONTH);

		assertThat(run().run(id)).isEqualTo(ReportStatus.READY);

		assertThat(this.requests).hasSize(1);
		String content = this.requests.getFirst().prompt().userContent();
		assertThat(content).contains("안의 첫 주 개요", "안의 둘째 주 개요")
			.doesNotContain("실패한 주 개요", "시작을 걸친 주 개요", "끝을 걸친 주 개요", "다른 사용자의 주 개요");
	}

	/**
	 * 같은 리포트의 생성 작업 둘(거두기의 다시 등록 · 다시 만들기로 생길 수 있다)이 함께 돌아도 생성기 호출은 하나다 — 시험이 리포트 행을 잠가
	 * 두 실행을 생성의 행 잠금 앞에 세운 뒤 풀어, 두 실행이 생성을 기다림을 함께 읽은 경우를 만든다. 행 잠금이 없으면 늦은 실행도 시작을 써
	 * 버전이 어긋나 예외로 끝난다.
	 */
	@Test
	void twoJobsRunningTheSameReportCallTheGeneratorOnce() throws Exception {
		UserId user = user("report-e2e-twice@example.com", FINANCE);
		ReportId id = schedule(user, WEEK);
		ReportRun run = run();
		CountDownLatch held = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(3);
		try {
			Future<?> holder = pool.submit(() -> this.transaction.executeWithoutResult(status -> {
				this.jdbc.sql("select id from reports where id = :id for update").param("id", id.value()).query().listOfRows();
				held.countDown();
				try {
					assertThat(release.await(30, TimeUnit.SECONDS)).isTrue();
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(ex);
				}
			}));
			assertThat(held.await(20, TimeUnit.SECONDS)).isTrue();
			Future<ReportStatus> first = pool.submit(() -> run.run(id));
			Future<ReportStatus> second = pool.submit(() -> run.run(id));
			// 두 실행이 모두 리포트 행의 잠금을 기다릴 때까지 — 재료 조립을 마치고 생성의 트랜잭션 ① 에서 행에 닿았다
			awaitLockWaiters(2);
			release.countDown();
			holder.get(20, TimeUnit.SECONDS);

			List<ReportStatus> statuses = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
			assertThat(statuses).contains(ReportStatus.READY)
				.allSatisfy(status -> assertThat(status).isIn(ReportStatus.READY, ReportStatus.RUNNING));
		}
		finally {
			release.countDown();
			pool.shutdownNow();
		}

		assertThat(this.requests).hasSize(1);
		assertThat(this.jdbc.sql("select status from reports where id = :id").param("id", id.value()).query(String.class).single())
			.isEqualTo("READY");
	}

	/** 리포트 행의 잠금을 기다리는 세션이 {@code count} 개가 될 때까지 — 어느 문장으로 기다리는지는 보지 않는다 */
	private void awaitLockWaiters(int count) throws InterruptedException {
		long deadline = System.nanoTime() + 30_000_000_000L;
		while (this.jdbc.sql("""
				select count(*) from pg_stat_activity
				where datname = current_database() and wait_event_type = 'Lock' and query like '%reports%'
				""").query(Long.class).single() < count) {
			assertThat(System.nanoTime()).as("행 잠금을 기다리는 실행 %d 개", count).isLessThan(deadline);
			Thread.sleep(20);
		}
	}

	private ReportRun run() {
		ReportGenerator generator = new ReportGenerator() {

			private final FakeReportGenerator fake = new FakeReportGenerator();

			@Override
			public ReportOutcome generate(ReportRequest request) {
				ReportRunEndToEndTests.this.requests.add(request);
				return this.fake.generate(request);
			}

		};
		ReportGeneration generation = new ReportGeneration(this.reports, this.sections, this.endings, generator,
				this.transaction, TestClocks.of(() -> NOW));
		return new ReportRun(this.reports, this.ledger, this.models, this.timezones, this.collection, generation,
				this.endings, this.transaction, () -> NOW);
	}

	private ReportId schedule(UserId user, ReportPeriod period) {
		return new ReportGeneration(this.reports, this.sections, this.endings, new FakeReportGenerator(),
				this.transaction, TestClocks.of(() -> NOW))
			.schedule(user, period)
			.id();
	}

	/** 버전 1 의 금융 키 finance 를 banking 으로 바꾼(계보 rename) 버전 2 를 켠다 */
	private TagSetId activateRenamedFinance(TagSetId first) {
		String text = this.administration.versionText(first).orElseThrow().replace("key: \"finance\"", "key: \"banking\"")
				+ "lineage:\n  - { kind: topic, change: rename, from: [economy.finance], to: [economy.banking] }\n";
		TagSetActivationResult result = this.administration.activate(text, 0, ChangeActor.OPERATOR);
		assertThat(result).isInstanceOf(TagSetActivationResult.Activated.class);
		return ((TagSetActivationResult.Activated) result).version().id();
	}

	/** 쓰는 계정 — 서울, 활성 버전, 그 주제 하나를 2026-11-01 부터 구독, OpenAI 유효 키 */
	private UserId user(String email, String topic) {
		UserId user = UserStorylineFixtures.user(this.jdbc, email);
		this.jdbc.sql("""
				insert into user_settings (user_id, tag_set_id, timezone, digest_time, weekly_report,
				    weekly_report_day, monthly_report, monthly_report_day, candidate_volume, big_slots, summary_length,
				    reasoning_effort, updated_at, version)
				values (:user, :tagSet, 'Asia/Seoul', '07:00', true, 1, true, 19, 'NORMAL', 3, 'NORMAL', 'MEDIUM', now(), 0)
				""").param("user", user.value()).param("tagSet", this.tagSets.activeTagSet().id().value()).update();
		Instant subscribed = Instant.parse("2026-11-01T00:00:00Z");
		this.jdbc.sql("""
				insert into user_tag_preferences (user_id, tag_kind, tag_key, mode, created_at)
				values (:user, 'TOPIC', :topic, 'SUBSCRIBE', :at)
				""").param("user", user.value()).param("topic", topic).param("at", subscribed.atOffset(ZoneOffset.UTC)).update();
		this.interests.subscribe(user.value(), topic, "ADD", subscribed);
		this.jdbc.sql("""
				insert into llm_credentials (user_id, provider, ciphertext, encrypted_dek, kek_version, key_hint, status,
				  validated_at, created_at)
				values (:user, 'openai', '\\x01'::bytea, '\\x01'::bytea, 1, '', 'VALID', now(), now())
				""").param("user", user.value()).update();
		return user;
	}

	/** 그 버전으로 한 번 평가한 기사 — 주제 하나(확률 0.9), 평가 시각 {@code at} */
	private void evaluated(String title, TagSetId version, String topic, String at) {
		long article = this.rows.article(title);
		long evaluation = this.jdbc.sql("""
				insert into article_evaluations (article_id, tag_set_id, judgement_id, format_key, format_probability,
				    format_distribution, importance, importance_distribution, evaluated_at)
				values (:article, :tagSet, :article, 'straight', 1.0, '{}', 3.0, '{}', :at)
				returning id
				""")
			.param("article", article)
			.param("tagSet", version.value())
			.param("at", Instant.parse(at).atOffset(ZoneOffset.UTC))
			.query(Long.class)
			.single();
		this.jdbc.sql("""
				insert into article_tags (evaluation_id, article_id, tag_kind, tag_key, probability)
				values (:evaluation, :article, 'TOPIC', :topic, 0.9)
				""").param("evaluation", evaluation).param("article", article).param("topic", topic).update();
	}

	/** 기사 하나만 든 사건 — 그 기사의 제목 · 발행 시각 */
	private long event(String title, String published) {
		long article = this.rows.article(title);
		long event = this.rows.event(1, published, published);
		this.rows.assign(article, event, published);
		return event;
	}

	/** 주간 리포트 하나와 그 개요 섹션 */
	private void weekly(UserId user, String status, String start, String end, String overview) {
		long id = this.reportRows.report(user.value(), "WEEK", status, Instant.parse(start), Instant.parse(end), "{}",
				null);
		this.reportRows.section(id, "OVERVIEW", 0, null, overview);
	}

	private Map<String, Object> row(ReportId id) {
		return this.jdbc.sql("""
				select stats_snapshot::text as stats_snapshot, previous_report_id, zone from reports where id = :id
				""").param("id", id.value()).query().singleRow();
	}

	/** 그 칸을 뺀 사본 */
	private static JsonNode without(JsonNode tree, String field) {
		ObjectNode copy = ((ObjectNode) tree).deepCopy();
		copy.remove(field);
		return copy;
	}

	/** 태그 체계 활성 전환을 받는 리스너가 끝날 때까지 — 버전을 지우기 전에 */
	private void awaitPublications() {
		long deadline = System.nanoTime() + 20_000_000_000L;
		while (System.nanoTime() < deadline && this.jdbc.sql("""
				select count(*) from event_publication
				where completion_date is null and coalesce(status, '') <> 'FAILED'
				""").query(Long.class).single() > 0) {
			Thread.onSpinWait();
		}
	}

}
