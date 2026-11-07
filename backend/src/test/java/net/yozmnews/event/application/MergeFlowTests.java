package net.yozmnews.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.evaluation.EvaluatorFailureKind;
import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.evaluation.EventMembership;
import net.yozmnews.evaluation.MembershipJudgement;
import net.yozmnews.evaluation.MembershipQuery;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.domain.assignment.AssignmentCriteria;
import net.yozmnews.event.domain.assignment.CriteriaRepository;
import net.yozmnews.event.domain.assignment.MergeJudgementRepository;
import net.yozmnews.event.domain.event.EventArticleRepository;
import net.yozmnews.event.domain.event.EventRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사건 병합 — 바뀐 열린 사건마다 가까운 열린 사건을 배정 산식 · 위 기준으로 합치고, 띠는 사건 소속 판정에 한 번 묻는다. 기사 수가 많은
 * 사건이 남고, 합쳐진 사건은 남는 사건을 가리킨다. 사건 · 배정 · 표현은 SQL 로 넣는다(기준 위 0.80 · 아래 0.55 · 저장 0.40 · 확률 0.50 ·
 * 창 72시간). 벡터는 반 정밀도로 정확한 값이다.
 * <p>
 * 지키는 규칙: event-R13
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class MergeFlowTests {

	private static final Instant T = Instant.parse("2026-11-05T00:00:00Z");

	private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

	private static final AssignmentCriteria CRITERIA = new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.55, 0.40,
			0.50);

	@Autowired
	EventRepository events;

	@Autowired
	EventArticleRepository assignments;

	@Autowired
	MergeJudgementRepository judgements;

	@Autowired
	CriteriaRepository criteriaHistory;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		EventArticles.clear(this.jdbc);
	}

	@Test
	void twoCloseEventsMergeIntoTheLargerOne() {
		long large = event("[1,0]", Instant.parse("2026-11-05T02:00:00Z"), Instant.parse("2026-11-05T03:00:00Z"),
				Map.of("금리", 3, "동결", 3), "[1,0]", "[1,0]", "[1,0]");
		// cos([1,0], [0.8,0.6]) = 0.8, 작은 쪽 핵심 말(금리)이 모두 큰 쪽에 있다 — 0.7 × 0.8 + 0.3 × 1 = 0.86 ≥ 위 기준
		long small = event("[0.8,0.6]", Instant.parse("2026-11-05T01:00:00Z"), Instant.parse("2026-11-05T04:00:00Z"),
				Map.of("금리", 1), "[0.8,0.6]");
		Scripted membership = new Scripted(query -> {
			throw new AssertionError("산식으로 합치므로 묻지 않는다: " + query);
		});

		round(membership).run();

		assertThat(eventRow(small)).containsEntry("status", "MERGED")
			.containsEntry("merged_into", large)
			.containsEntry("closed", "2026-11-06T00:00:00Z");
		Map<String, @Nullable Object> merged = eventRow(large);
		assertThat(merged).containsEntry("status", "OPEN")
			.containsEntry("article_count", 4)
			.containsEntry("opened", "2026-11-05T01:00:00Z")
			.containsEntry("joined", "2026-11-05T04:00:00Z");
		// ([1,0] × 3 + [0.8,0.6] × 1) / 4 = [0.95, 0.15]
		float[] centroid = centroid(large);
		assertThat((double) centroid[0]).isCloseTo(0.95, within(1e-3));
		assertThat((double) centroid[1]).isCloseTo(0.15, within(1e-3));
		assertThat(termCounts(large)).containsExactlyInAnyOrderEntriesOf(Map.of("금리", 4, "동결", 3));
		assertThat(termCounts(small)).isEmpty();
		assertThat(this.jdbc.sql("select event_id, opened from event_articles order by article_id")
			.query()
			.listOfRows()).containsExactly(Map.of("event_id", large, "opened", true),
					Map.of("event_id", large, "opened", false), Map.of("event_id", large, "opened", false),
					Map.of("event_id", large, "opened", true));
		assertThat(membership.queries).isEmpty();
	}

	@Test
	void aBandPairIsAskedOnceAndMergedByTheEvaluator() {
		// 핵심 말이 겹치지 않는다 — 0.7 × 0.8 = 0.56(띠)
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		Scripted membership = new Scripted(query -> new MembershipJudgement.Judged(new EventJudgementId(1), Map.of(small, 0.8)));
		List<Long> largeMembers = members(large);
		List<Long> smallMembers = members(small);

		round(membership).run();

		assertThat(membership.queries).hasSize(1);
		MembershipQuery query = membership.queries.get(0);
		// 판정할 기사는 바뀐 사건(large — 먼저 본다)의 서술 기사, 후보는 상대 사건과 그 서술 기사다
		assertThat(query.articleId().value()).isIn(largeMembers);
		assertThat(query.candidates()).singleElement().satisfies(candidate -> {
			assertThat(candidate.eventId()).isEqualTo(small);
			assertThat(candidate.articleIds()).extracting(id -> id.value()).containsExactlyElementsOf(smallMembers);
		});
		assertThat(membership.inTransaction).containsExactly(false);
		assertThat(eventRow(small)).containsEntry("status", "MERGED").containsEntry("merged_into", large);
		assertThat(judgementRows()).containsExactly(Map.of("event_low", large, "event_high", small, "probability", 0.8));
	}

	@Test
	void aLowProbabilityKeepsThePairApartAndIsNotAskedAgain() {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		Scripted membership = new Scripted(query -> new MembershipJudgement.Judged(new EventJudgementId(1), Map.of(small, 0.3)));

		round(membership).run();
		// 두 사건이 모두 바뀌었다고 표시해도 남은 확률로 정해 다시 묻지 않는다
		this.jdbc.sql("update events set revision = revision + 1").update();
		round(membership).run();

		assertThat(membership.queries).hasSize(1);
		assertThat(eventRow(large)).containsEntry("status", "OPEN").containsEntry("article_count", 2);
		assertThat(eventRow(small)).containsEntry("status", "OPEN").containsEntry("article_count", 1);
		assertThat(this.jdbc.sql("select count(*) from events where merge_checked_revision = revision")
			.query(Long.class)
			.single()).isEqualTo(2);
	}

	@Test
	void aMergedRowFollowsTheNextSurvivor() {
		long x = event("[1,0]", T, T, Map.of("금리", 1), "[1,0]");
		long y = event("[1,0]", T, T, Map.of("금리", 2), "[1,0]", "[1,0]");
		long z = event("[1,0]", T, T, Map.of("금리", 4), "[1,0]", "[1,0]", "[1,0]", "[1,0]");

		// x 를 보며 점수가 같은 y · z 가운데 ID 가 작은 y 에 합치고(y 가 남는다), z 를 보며 y 를 z 에 합친다(z 가 더 크다)
		round(new Scripted(query -> {
			throw new AssertionError("묻지 않는다");
		})).run();

		assertThat(eventRow(x)).containsEntry("status", "MERGED").containsEntry("merged_into", z);
		assertThat(eventRow(y)).containsEntry("status", "MERGED").containsEntry("merged_into", z);
		assertThat(eventRow(z)).containsEntry("status", "OPEN").containsEntry("article_count", 7);
		assertThat(this.jdbc.sql("select distinct event_id from event_articles").query(Long.class).list())
			.containsExactly(z);
	}

	@Test
	void runningAgainChangesNothing() {
		long large = event("[1,0]", T, T, Map.of("금리", 3, "동결", 3), "[1,0]", "[1,0]", "[1,0]");
		event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		event("[0,1]", T, T, Map.of("환율", 1), "[0,1]");
		Scripted membership = new Scripted(query -> new MembershipJudgement.Judged(null, Map.of()));
		round(membership).run();
		// 남는 사건은 revision 이 늘어 한 번 더 본다 — 그 뒤로는 볼 것이 없다
		round(membership).run();
		List<Map<String, Object>> before = snapshot();

		round(membership).run();

		assertThat(snapshot()).isEqualTo(before);
		assertThat(eventRow(large)).containsEntry("article_count", 4);
		assertThat(membership.queries).isEmpty();
	}

	@Test
	void aTransientFailureLeavesTheEventUncheckedAndTheRoundGoesOn(CapturedOutput output) {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		// 떨어진 다른 쌍 — 산식으로 합친다
		long other = event("[0,1]", T, T, Map.of("환율", 2), "[0,1]", "[0,1]");
		long otherSmall = event("[0,1]", T, T, Map.of("환율", 1), "[0,1]");
		Scripted membership = new Scripted(query -> new MembershipJudgement.Failed(EvaluatorFailureKind.TRANSIENT,
				"요청 제한"));

		round(membership).run();

		assertThat(this.jdbc.sql("select merge_checked_revision from events where id = :id")
			.param("id", large)
			.query(Long.class)
			.single()).isZero();
		assertThat(eventRow(small)).containsEntry("status", "OPEN");
		assertThat(eventRow(otherSmall)).containsEntry("status", "MERGED").containsEntry("merged_into", other);
		assertThat(judgementRows()).isEmpty();
		assertThat(output.getOut()).contains("사건 병합 판정이 일시 실패해 다음 회차에 다시 본다 — 사건 " + large + ": 요청 제한");
	}

	@Test
	void anUnavailableEvaluatorStopsTheRound(CapturedOutput output) {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		long other = event("[0,1]", T, T, Map.of("환율", 2), "[0,1]", "[0,1]");
		long otherSmall = event("[0,1]", T, T, Map.of("환율", 1), "[0,1]");
		Scripted membership = new Scripted(query -> new MembershipJudgement.Failed(
				EvaluatorFailureKind.EVALUATOR_UNAVAILABLE, "키가 무효다"));

		round(membership).run();

		// 첫 사건에서 멈춰 뒤의 쌍은 보지 않았다
		assertThat(eventRow(otherSmall)).containsEntry("status", "OPEN");
		assertThat(eventRow(other)).containsEntry("article_count", 2);
		assertThat(output.getOut()).contains("평가기를 쓸 수 없어 병합 회차를 멈췄다 — 사건 " + large + ": 키가 무효다");
	}

	@Test
	void aRejectedArticleDecidesWithoutAProbabilityAndMarksTheEvent(CapturedOutput output) {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		Scripted membership = new Scripted(query -> new MembershipJudgement.Failed(EvaluatorFailureKind.ARTICLE_REJECTED,
				"본문 거부"));

		round(membership).run();

		// 띠 후보는 확률 없이 붙일 수 없다 — 합치지 않고, 두 사건 모두 확인을 표시해 다음 회차에 다시 묻지 않는다
		assertThat(membership.queries).hasSize(2);
		assertThat(eventRow(large)).containsEntry("status", "OPEN").containsEntry("article_count", 2);
		assertThat(eventRow(small)).containsEntry("status", "OPEN").containsEntry("article_count", 1);
		assertThat(this.jdbc.sql("select count(*) from events where merge_checked_revision = revision")
			.query(Long.class)
			.single()).isEqualTo(2);
		assertThat(judgementRows()).isEmpty();
		assertThat(output.getOut()).contains("평가기가 기사를 거부해 병합 판정을 확률 없이 정한다 — 사건 " + large + ": 본문 거부");
	}

	@Test
	void aMissingArticleLeavesTheEventsUnchecked() {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		Scripted membership = new Scripted(query -> new MembershipJudgement.ArticleMissing());

		round(membership).run();

		// 판정할 기사가 그새 지워졌다 — 표시하지 않아 다음 회차가 다른 서술 기사로 다시 묻는다
		assertThat(membership.queries).hasSize(2);
		assertThat(eventRow(large)).containsEntry("status", "OPEN");
		assertThat(eventRow(small)).containsEntry("status", "OPEN");
		assertThat(this.jdbc.sql("select merge_checked_revision from events order by id").query(Long.class).list())
			.containsExactly(0L, 0L);
		assertThat(judgementRows()).isEmpty();
	}

	private MergeRound round(EventMembership membership) {
		return new MergeRound(this.events, this.assignments, this.judgements, this.criteriaHistory, CRITERIA, membership,
				this.transaction, (InstantSource) () -> NOW);
	}

	/**
	 * 열린 사건 하나를 SQL 로 넣는다 — 구성 기사마다 기사 · 표현(모델 a) · 배정(첫 기사가 연 기사)을 넣는다. 첫 · 마지막 기사 시각은 T.
	 * @param opened 연 시각
	 * @param joined 마지막으로 붙은 시각
	 */
	private long event(String centroid, Instant opened, Instant joined, Map<String, Integer> terms,
			String... memberVectors) {
		long event = this.jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at, last_joined_at)
				values ('OPEN', 'model-a', cast(:centroid as halfvec), :count, :t, :t, :opened, :joined) returning id
				""")
			.param("centroid", centroid)
			.param("count", memberVectors.length)
			.param("t", T.atOffset(ZoneOffset.UTC))
			.param("opened", opened.atOffset(ZoneOffset.UTC))
			.param("joined", joined.atOffset(ZoneOffset.UTC))
			.query(Long.class)
			.single();
		terms.forEach((term, count) -> this.jdbc
			.sql("insert into event_key_terms (event_id, term, article_count) values (:event, :term, :count)")
			.param("event", event)
			.param("term", term)
			.param("count", count)
			.update());
		long criteria = this.criteriaHistory.register(CRITERIA, T);
		for (int i = 0; i < memberVectors.length; i++) {
			long article = EventArticles.insert(this.jdbc, "병합 시험 기사", null);
			this.jdbc.sql("""
					insert into article_match_features (article_id, model_version, embedding, entities, created_at)
					values (:article, 'model-a', cast(:vector as vector), '{}', :t)
					""").param("article", article).param("vector", memberVectors[i]).param("t", T.atOffset(ZoneOffset.UTC))
				.update();
			this.jdbc.sql("""
					insert into event_articles (article_id, event_id, published_at, decided_by, opened, criteria_id,
					                            assigned_at)
					values (:article, :event, :t, 'FORMULA', :opened, :criteria, :t)
					""")
				.param("article", article)
				.param("event", event)
				.param("t", T.atOffset(ZoneOffset.UTC))
				.param("opened", i == 0)
				.param("criteria", criteria)
				.update();
		}
		return event;
	}

	private Map<String, @Nullable Object> eventRow(long event) {
		return this.jdbc.sql("""
				select status, merged_into, article_count,
				    to_char(closed_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as closed,
				    to_char(created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as opened,
				    to_char(last_joined_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as joined
				from events where id = :event
				""").param("event", event).query().singleRow();
	}

	private float[] centroid(long event) {
		String text = this.jdbc.sql("select centroid::text from events where id = :event")
			.param("event", event)
			.query(String.class)
			.single();
		String[] parts = text.substring(1, text.length() - 1).split(",");
		float[] values = new float[parts.length];
		for (int i = 0; i < parts.length; i++) {
			values[i] = Float.parseFloat(parts[i]);
		}
		return values;
	}

	private List<Long> members(long event) {
		return this.jdbc.sql("select article_id from event_articles where event_id = :event order by article_id")
			.param("event", event)
			.query(Long.class)
			.list();
	}

	private Map<String, Integer> termCounts(long event) {
		Map<String, Integer> counts = new HashMap<>();
		this.jdbc.sql("select term, article_count from event_key_terms where event_id = :event")
			.param("event", event)
			.query((java.sql.ResultSet rs) -> {
				counts.put(rs.getString("term"), rs.getInt("article_count"));
			});
		return counts;
	}

	private List<Map<String, Object>> judgementRows() {
		return this.jdbc.sql("select event_low, event_high, probability from event_merge_judgements order by event_low")
			.query()
			.listOfRows();
	}

	private List<Map<String, Object>> snapshot() {
		List<Map<String, Object>> rows = new ArrayList<>();
		rows.addAll(this.jdbc.sql("""
				select id, status, merged_into, article_count, centroid::text as centroid, revision,
				    merge_checked_revision, created_at, last_joined_at, closed_at
				from events order by id
				""").query().listOfRows());
		rows.addAll(this.jdbc.sql("select * from event_articles order by article_id").query().listOfRows());
		rows.addAll(this.jdbc.sql("select * from event_key_terms order by event_id, term").query().listOfRows());
		rows.addAll(this.jdbc.sql("select * from event_merge_judgements order by event_low").query().listOfRows());
		return rows;
	}

	/** 정해 둔 답을 내고, 받은 요청과 그때 트랜잭션이 있었는지를 기록한다 */
	private static final class Scripted implements EventMembership {

		final List<MembershipQuery> queries = new ArrayList<>();

		final List<Boolean> inTransaction = new ArrayList<>();

		private final Function<MembershipQuery, MembershipJudgement> answer;

		Scripted(Function<MembershipQuery, MembershipJudgement> answer) {
			this.answer = answer;
		}

		@Override
		public MembershipJudgement judge(MembershipQuery query) {
			this.queries.add(query);
			this.inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
			return this.answer.apply(query);
		}

	}

}
