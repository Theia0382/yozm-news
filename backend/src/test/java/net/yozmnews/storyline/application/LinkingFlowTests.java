package net.yozmnews.storyline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventProfile;
import net.yozmnews.event.EventView;
import net.yozmnews.event.EventsChanged;
import net.yozmnews.platform.VectorText;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import net.yozmnews.storyline.domain.linking.LinkingCriteriaRepository;
import net.yozmnews.storyline.domain.queue.LinkingQueueRepository;
import net.yozmnews.storyline.domain.storyline.StorylineRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 잇기 회차의 흐름 — 짝으로 열기, 붙이기, 소속 없이 남기, 합쳐진 사건 · 지워진 사건 따르기와 빈 스토리라인 지우기, 소속이 늘 때 이웃 다시
 * 넣기와 옮기기는 계기가 아님, 처리 중에 다시 온 알림, 실패 상한, 리스너의 순서 키를 실제 PostgreSQL 과 실제 저장소 · 사건 조회로 본다.
 * 사건은 SQL 로 넣고 대기에는 시험이 직접 넣는다. 회차 · 판정은 시험이 고정 시계로 직접 만든다(작업 서버는 끈다). 다른 시험 클래스의 비동기
 * 리스너가 남긴 대기 행이 섞일 수 있어, 대기 · 스토리라인 단언은 이 시험의 사건 · 모델 버전으로 좁힌다.
 * <p>
 * 기준: 창 14일 · 기준 0.65 — 점수 = 감쇠 × (0.7 × 코사인 + 0.3 × 겹침). 벡터는 반 정밀도에서 정확한 성분이다.
 * <p>
 * 지키는 규칙: storyline-R1 ~ storyline-R14
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class LinkingFlowTests {

	private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

	private static final String MODEL = "link-m";

	private static final LinkingCriteria CRITERIA = new LinkingCriteria(Duration.ofDays(14), 0.65);

	@Autowired
	EventDirectory directory;

	@Autowired
	StorylineRepository storylines;

	@Autowired
	LinkingQueueRepository queue;

	@Autowired
	LinkingCriteriaRepository criteria;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		StorylineRows.clear(this.jdbc);
	}

	@Test
	void twoCloseEventsOpenAStoryline() {
		Pair pair = openPair();

		long storyline = onlyStoryline();
		// cos([1,0], [4,3]) = 0.8, 핵심 말 둘이 모두 겹친다 — 0.7 × 0.8 + 0.3 × 1 = 0.86
		assertThat(member(storyline, pair.e1())).containsEntry("linked_by", "PAIR")
			.containsEntry("cosine", 0.8)
			.containsEntry("key_term_overlap", 1.0)
			.containsEntry("criteria_id", criteriaId());
		assertThat((Double) member(storyline, pair.e1()).get("score")).isCloseTo(0.86, within(1e-6));
		assertThat(member(storyline, pair.e2())).containsEntry("linked_by", "PAIR");
		// ([1,0] × 3 + [4,3] × 1) / 4 = [1.75, 0.75] — 대표는 기사 셋인 e1
		assertThat(storylineRow(storyline)).containsEntry("centroid", "[1.75,0.75]")
			.containsEntry("article_count", 4)
			.containsEntry("representative_event_id", pair.e1())
			.containsEntry("first", "2026-11-05T00:00:00Z")
			.containsEntry("last", "2026-11-05T06:00:00Z");
		assertThat(terms(storyline)).containsExactlyInAnyOrderEntriesOf(Map.of("금리", 4, "물가", 3));
		assertThat(queued(pair.e1())).isEmpty();
		assertThat(queued(pair.e2())).isEmpty();

		// 같은 사건을 다시 판정해도 스토리라인은 하나이고 ID 가 그대로다(증분)
		this.queue.enqueue(pair.e1(), null, NOW);
		this.queue.enqueue(pair.e2(), null, NOW);
		round(this.directory).run();
		assertThat(onlyStoryline()).isEqualTo(storyline);
		assertThat(memberIds(storyline)).containsExactly(pair.e1(), pair.e2());
	}

	@Test
	void aLaterEventJoinsTheStoryline() {
		Pair pair = openPair();
		long storyline = onlyStoryline();
		long e3 = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[1,0]", 1, "2026-11-05T04:00:00Z",
				"2026-11-05T04:00:00Z", Map.of("금리", 1));
		this.queue.enqueue(e3, Instant.parse("2026-11-05T04:00:00Z"), NOW);

		round(this.directory).run();

		// cos([1,0], [1.75,0.75]) = 1.75 / √3.625 ≈ 0.919145, 겹침 1, 범위 안 — 0.7 × 0.919145 + 0.3 = 0.94340
		assertThat(member(storyline, e3)).containsEntry("linked_by", "RULE");
		assertThat((Double) member(storyline, e3).get("score")).isCloseTo(0.94340, within(1e-4));
		assertThat(memberIds(storyline)).containsExactly(pair.e1(), pair.e2(), e3);
		// ([1,0] × 3 + [4,3] + [1,0]) / 5 = [1.6, 0.6] — 반 정밀도로 저장해 1e-3 안
		assertThat(storylineRow(storyline)).containsEntry("article_count", 5);
		float[] centroid = centroid(storyline);
		assertThat((double) centroid[0]).isCloseTo(1.6, within(1e-3));
		assertThat((double) centroid[1]).isCloseTo(0.6, within(1e-3));
	}

	@Test
	void aLoneEventStaysUnlinked() {
		long e1 = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[1,0]", 1, "2026-11-05T00:00:00Z",
				"2026-11-05T00:00:00Z", Map.of("금리", 1));
		long e2 = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[0,1]", 1, "2026-11-05T00:00:00Z",
				"2026-11-05T00:00:00Z", Map.of("환율", 1));
		this.queue.enqueue(e1, Instant.parse("2026-11-05T00:00:00Z"), NOW);
		this.queue.enqueue(e2, Instant.parse("2026-11-05T00:00:00Z"), NOW);

		round(this.directory).run();

		assertThat(storylineIds()).isEmpty();
		assertThat(queued(e1)).isEmpty();
		assertThat(queued(e2)).isEmpty();
	}

	@Test
	void aMergedEventMovesItsMembershipToTheSurvivor() {
		Pair pair = openPair();
		long storyline = onlyStoryline();
		long e9 = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[1,0]", 2, "2026-11-05T01:00:00Z",
				"2026-11-05T02:00:00Z", Map.of("금리", 2));
		StorylineRows.merge(this.jdbc, pair.e2(), e9);
		this.queue.enqueue(pair.e2(), null, NOW);

		round(this.directory).run();

		assertThat(memberIds(storyline)).containsExactly(pair.e1(), e9);
		assertThat(member(storyline, e9)).containsEntry("linked_by", "MERGE")
			.containsEntry("score", null)
			.containsEntry("criteria_id", null);
		// e1 [1,0] × 3 + e9 [1,0] × 2
		assertThat(storylineRow(storyline)).containsEntry("centroid", "[1,0]").containsEntry("article_count", 5);
	}

	@Test
	void aMergedEventIntoAMemberLeavesOneMembership() {
		Pair pair = openPair();
		long storyline = onlyStoryline();
		StorylineRows.merge(this.jdbc, pair.e2(), pair.e1());
		this.queue.enqueue(pair.e2(), null, NOW);

		round(this.directory).run();

		assertThat(memberIds(storyline)).containsExactly(pair.e1());
		assertThat(member(storyline, pair.e1())).containsEntry("linked_by", "PAIR");
		assertThat(storylineRow(storyline)).containsEntry("centroid", "[1,0]").containsEntry("article_count", 3);
	}

	@Test
	void deletedEventsLeaveAndTheEmptiedStorylineGoes() {
		Pair pair = openPair();
		long storyline = onlyStoryline();
		StorylineRows.delete(this.jdbc, pair.e1());
		this.queue.enqueue(pair.e1(), null, NOW);

		round(this.directory).run();

		assertThat(memberIds(storyline)).containsExactly(pair.e2());
		assertThat(storylineRow(storyline)).containsEntry("centroid", "[4,3]")
			.containsEntry("article_count", 1)
			.containsEntry("representative_event_id", pair.e2());

		StorylineRows.delete(this.jdbc, pair.e2());
		this.queue.enqueue(pair.e2(), null, NOW);
		round(this.directory).run();

		assertThat(storylineIds()).isEmpty();
		assertThat(this.jdbc.sql("select count(*) from storyline_key_terms where storyline_id = :id")
			.param("id", storyline)
			.query(Long.class)
			.single()).isZero();
		assertThat(this.jdbc.sql("select count(*) from storyline_members where storyline_id = :id")
			.param("id", storyline)
			.query(Long.class)
			.single()).isZero();
	}

	@Test
	void aWaitingEventIsRequeuedWhenANeighbourJoins() {
		// e1 은 혼자일 때 판정되어 소속 없이 남는다
		long e1 = StorylineRows.event(this.jdbc, "CLOSED", MODEL, "[4,3]", 1, "2026-11-01T00:00:00Z",
				"2026-11-01T00:00:00Z", Map.of("금리", 1));
		this.queue.enqueue(e1, Instant.parse("2026-11-01T00:00:00Z"), NOW);
		round(this.directory).run();
		assertThat(storylineIds()).isEmpty();
		assertThat(queued(e1)).isEmpty();

		// e2 · e3 이 생긴다 — e2 의 짝은 e3(코사인 1, 점수 1.0)이고, e1 은 e2 와 0.86 × 2^(−1/14) ≈ 0.818 로 기준 이상이다
		long e2 = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[1,0]", 2, "2026-11-02T00:00:00Z",
				"2026-11-02T00:00:00Z", Map.of("금리", 2));
		long e3 = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[1,0]", 1, "2026-11-02T00:00:00Z",
				"2026-11-02T00:00:00Z", Map.of("금리", 1));
		this.queue.enqueue(e2, Instant.parse("2026-11-02T00:00:00Z"), NOW);
		round(this.directory).run();

		long storyline = onlyStoryline();
		assertThat(memberIds(storyline)).containsExactly(e2, e3);
		// 소속이 늘어 e1 을 다시 넣었다 — 순서 키는 e1 의 첫 기사 시각
		assertThat(queued(e1)).hasValueSatisfying(row -> assertThat(row).containsEntry("order_at", "2026-11-01T00:00:00Z"));

		round(this.directory).run();

		// e1 과 표현 [1,0] 의 점수도 ≈ 0.818 — 붙는다
		assertThat(memberIds(storyline)).containsExactly(e1, e2, e3);
		assertThat(member(storyline, e1)).containsEntry("linked_by", "RULE");
	}

	@Test
	void aWaitingEventBelowTheThresholdIsNotRequeued() {
		long e1 = StorylineRows.event(this.jdbc, "CLOSED", MODEL, "[0,1]", 1, "2026-11-01T00:00:00Z",
				"2026-11-01T00:00:00Z", Map.of("환율", 1));
		long e2 = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[1,0]", 2, "2026-11-02T00:00:00Z",
				"2026-11-02T00:00:00Z", Map.of("금리", 2));
		StorylineRows.event(this.jdbc, "OPEN", MODEL, "[1,0]", 1, "2026-11-02T00:00:00Z", "2026-11-02T00:00:00Z",
				Map.of("금리", 1));
		this.queue.enqueue(e2, Instant.parse("2026-11-02T00:00:00Z"), NOW);

		round(this.directory).run();

		assertThat(storylineIds()).hasSize(1);
		assertThat(queued(e1)).isEmpty();
	}

	@Test
	void aMergeMoveDoesNotRequeueNeighbours() {
		Pair pair = openPair();
		long storyline = onlyStoryline();
		// e5 는 e9 와 코사인 1 · 겹침 1 이라 e9 가 판정으로 소속을 얻었다면 다시 넣었을 이웃이다
		long e5 = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[1,0]", 1, "2026-11-05T01:00:00Z",
				"2026-11-05T01:00:00Z", Map.of("금리", 1));
		long e9 = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[1,0]", 2, "2026-11-05T01:00:00Z",
				"2026-11-05T01:00:00Z", Map.of("금리", 2));
		StorylineRows.merge(this.jdbc, pair.e2(), e9);
		// 합치기는 두 사건을 함께 알린다 — e9 도 판정하지만 e9 는 이미 든 L 밖에 붙을 곳이 없어 소속이 늘지 않는다
		this.queue.enqueue(pair.e2(), null, NOW);
		this.queue.enqueue(e9, Instant.parse("2026-11-05T01:00:00Z"), NOW);

		round(this.directory).run();

		assertThat(memberIds(storyline)).containsExactly(pair.e1(), e9);
		assertThat(queued(e5)).isEmpty();
		assertThat(queued(e9)).isEmpty();
	}

	@Test
	void theJudgementHoldsTheModuleLock() {
		long e1 = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[1,0]", 1, "2026-11-05T00:00:00Z",
				"2026-11-05T00:00:00Z", Map.of("금리", 1));
		this.queue.enqueue(e1, Instant.parse("2026-11-05T00:00:00Z"), NOW);
		List<Long> held = new ArrayList<>();
		EventDirectory observing = new ForwardingEventDirectory(this.directory) {
			@Override
			public Optional<EventView> find(EventId id) {
				// 판정 트랜잭션 안 — 같은 연결에서 이 모듈의 advisory 잠금('STRY', 0)을 본다
				held.add(LinkingFlowTests.this.jdbc.sql("""
						select count(*) from pg_locks
						where locktype = 'advisory' and classid = 1398035033 and objid = 0 and pid = pg_backend_pid()
						""").query(Long.class).single());
				return super.find(id);
			}
		};

		round(observing).run();

		assertThat(held).containsExactly(1L);
		assertThat(queued(e1)).isEmpty();
	}

	@Test
	void aNotificationDuringJudgementKeepsTheRow() {
		long e1 = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[1,0]", 1, "2026-11-05T00:00:00Z",
				"2026-11-05T00:00:00Z", Map.of("금리", 1));
		this.queue.enqueue(e1, Instant.parse("2026-11-05T00:00:00Z"), NOW);
		AtomicBoolean interrupted = new AtomicBoolean();
		EventDirectory notifying = new ForwardingEventDirectory(this.directory) {
			@Override
			public Map<EventId, EventProfile> profiles(Collection<EventId> ids, int keyTerms) {
				if (interrupted.compareAndSet(false, true)) {
					// 판정 도중 다른 트랜잭션에서 같은 사건의 알림이 온다
					CompletableFuture.runAsync(() -> LinkingFlowTests.this.transaction
						.executeWithoutResult(status -> LinkingFlowTests.this.queue.enqueue(e1,
								Instant.parse("2026-11-05T00:00:00Z"), NOW)))
						.join();
				}
				return super.profiles(ids, keyTerms);
			}
		};

		round(notifying).run();

		// 다시 알림 수가 점유할 때(0)와 달라 지우지 않고 점유만 풀었다
		assertThat(queued(e1)).hasValueSatisfying(
				row -> assertThat(row).containsEntry("notifications", 1).containsEntry("lease_token", null));

		round(this.directory).run();
		assertThat(queued(e1)).isEmpty();
	}

	@Test
	void aFailingEventIsSkippedAndDroppedAfterThreeFailures(CapturedOutput output) {
		long e1 = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[1,0]", 1, "2026-11-01T00:00:00Z",
				"2026-11-01T00:00:00Z", Map.of("금리", 1));
		long e2 = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[0,1]", 1, "2026-11-02T00:00:00Z",
				"2026-11-02T00:00:00Z", Map.of("환율", 1));
		this.queue.enqueue(e1, Instant.parse("2026-11-01T00:00:00Z"), NOW);
		this.queue.enqueue(e2, Instant.parse("2026-11-02T00:00:00Z"), NOW);
		EventDirectory failing = new ForwardingEventDirectory(this.directory) {
			@Override
			public Optional<EventView> find(EventId id) {
				if (id.value() == e1) {
					throw new IllegalStateException("시험이 넣은 실패");
				}
				return super.find(id);
			}
		};

		round(failing).run();

		// 앞 순서의 e1 이 실패해도 뒤의 e2 는 같은 회차에 처리된다
		assertThat(queued(e2)).isEmpty();
		assertThat(queued(e1)).hasValueSatisfying(
				row -> assertThat(row).containsEntry("failures", 1).containsEntry("lease_token", null));
		assertThat(output).contains("스토리라인 잇기 판정이 예상하지 못한 오류로 실패했다 — 사건 " + e1 + ", 실패 1회");

		round(failing).run();
		assertThat(queued(e1)).hasValueSatisfying(row -> assertThat(row).containsEntry("failures", 2));
		round(failing).run();

		assertThat(queued(e1)).isEmpty();
		assertThat(output).contains("스토리라인 잇기 판정이 거듭 실패해 대기에서 뺐다 — 사건 " + e1 + ", 실패 3회");
	}

	@Test
	void theListenerKeysTheQueueByTheEventsFirstArticle() {
		long a = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[1,0]", 1, "2026-11-03T00:00:00Z",
				"2026-11-04T00:00:00Z", Map.of());
		long c = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[1,0]", 1, "2026-11-01T00:00:00Z",
				"2026-11-01T00:00:00Z", Map.of());
		StorylineRows.merge(this.jdbc, c, a);
		long b = 999_999_999L;
		this.queue.enqueue(a, Instant.parse("2026-11-01T00:00:00Z"), NOW);
		StorylineLinkingListener listener = new StorylineLinkingListener(this.directory, this.queue,
				InstantSource.fixed(NOW));

		listener.on(new EventsChanged(List.of(new EventId(c), new EventId(b), new EventId(a))));

		assertThat(queued(a)).hasValueSatisfying(row -> assertThat(row).containsEntry("order_at", "2026-11-03T00:00:00Z")
			.containsEntry("notifications", 1));
		assertThat(queued(b)).hasValueSatisfying(row -> assertThat(row).containsEntry("order_at", null)
			.containsEntry("notifications", 0));
		assertThat(queued(c)).hasValueSatisfying(row -> assertThat(row).containsEntry("order_at", null));
	}

	/** e1([1,0] 기사 3, 금리 3 · 물가 2) · e2([4,3] 기사 1, 금리 · 물가)를 넣고 회차 한 번으로 둘이 연 스토리라인을 만든다 */
	private Pair openPair() {
		long e1 = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[1,0]", 3, "2026-11-05T00:00:00Z",
				"2026-11-05T06:00:00Z", Map.of("금리", 3, "물가", 2));
		long e2 = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[4,3]", 1, "2026-11-05T03:00:00Z",
				"2026-11-05T03:00:00Z", Map.of("금리", 1, "물가", 1));
		this.queue.enqueue(e1, Instant.parse("2026-11-05T00:00:00Z"), NOW);
		this.queue.enqueue(e2, Instant.parse("2026-11-05T03:00:00Z"), NOW);
		round(this.directory).run();
		return new Pair(e1, e2);
	}

	private LinkingRound round(EventDirectory events) {
		return new LinkingRound(this.queue, this.criteria, CRITERIA,
				new StorylineLinker(events, this.storylines, this.queue), this.transaction, InstantSource.fixed(NOW));
	}

	private long criteriaId() {
		return this.jdbc.sql("select id from storyline_linking_criteria where window_seconds = 1209600 and threshold = 0.65")
			.query(Long.class)
			.single();
	}

	private List<Long> storylineIds() {
		return this.jdbc.sql("select id from storylines where model_version = :model order by id")
			.param("model", MODEL)
			.query(Long.class)
			.list();
	}

	private long onlyStoryline() {
		List<Long> ids = storylineIds();
		assertThat(ids).hasSize(1);
		return ids.getFirst();
	}

	private List<Long> memberIds(long storyline) {
		return this.jdbc.sql("select event_id from storyline_members where storyline_id = :id order by event_id")
			.param("id", storyline)
			.query(Long.class)
			.list();
	}

	private Map<String, @Nullable Object> member(long storyline, long event) {
		return this.jdbc.sql("""
				select linked_by, score, cosine, key_term_overlap, criteria_id from storyline_members
				where storyline_id = :storyline and event_id = :event
				""").param("storyline", storyline).param("event", event).query().singleRow();
	}

	private Map<String, @Nullable Object> storylineRow(long storyline) {
		return this.jdbc.sql("""
				select centroid::text as centroid, article_count, representative_event_id,
				       to_char(first_article_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as first,
				       to_char(last_article_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as last
				from storylines where id = :id
				""").param("id", storyline).query().singleRow();
	}

	private float[] centroid(long storyline) {
		return VectorText.parse(this.jdbc.sql("select centroid::text from storylines where id = :id")
			.param("id", storyline)
			.query(String.class)
			.single());
	}

	private Map<String, Integer> terms(long storyline) {
		return this.jdbc.sql("select term, article_count from storyline_key_terms where storyline_id = :id")
			.param("id", storyline)
			.query((rs, row) -> Map.entry(rs.getString("term"), rs.getInt("article_count")))
			.list()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private Optional<Map<String, @Nullable Object>> queued(long event) {
		return this.jdbc.sql("""
				select notifications, failures, lease_token,
				       to_char(order_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as order_at
				from storyline_linking_queue where event_id = :event
				""").param("event", event).query().listOfRows().stream().findFirst();
	}

	private record Pair(long e1, long e2) {
	}

}
