package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.ResubmissionProbeEvent.Behavior;
import net.yozmnews.platform.application.EventResubmissionService;
import net.yozmnews.platform.domain.RegistryRows;
import net.yozmnews.platform.domain.RegistryRows.RegistryRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 남겨진 발행의 목록 · 다시 처리 · 폐기 — 실제 PostgreSQL 의 이벤트 발행 레지스트리로 본다.
 * <p>
 * 운영자 요청처럼 화면 역할만 켠다 — 멈춤 감시가 돌지 않아 시험이 넣은 오래된 발행 시각의 행이 바뀌지 않는다.
 * <p>
 * 지키는 규칙: platform-R14 · platform-R15 · platform-R16
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles(WebRole.PROFILE)
@ExtendWith(OutputCaptureExtension.class)
class EventPublicationAdministrationTests {

	private static final Duration TIMEOUT = Duration.ofSeconds(60);

	private static final String PROBE_EVENT = "net.yozmnews.platform.ResubmissionProbeEvent";

	private static final String MISSING_EVENT = "net.yozmnews.platform.NoSuchProbeEvent";

	private static final Instant T1 = Instant.parse("2026-10-01T00:00:00Z");

	private static final Instant T2 = Instant.parse("2026-10-02T00:00:00Z");

	private static final Instant T3 = Instant.parse("2026-10-03T00:00:00Z");

	@Autowired
	JdbcClient jdbc;

	@Autowired
	EventPublicationAdministration administration;

	@Autowired
	EventResubmissionService resubmission;

	@Autowired
	RegistryRows registryRows;

	@Autowired
	ResubmissionProbeListener listener;

	EventPublicationRows rows;

	/** 시험 리스너로 만든 발행 ID → 이벤트의 probe ID */
	private final Map<UUID, String> probes = new HashMap<>();

	@BeforeEach
	void setUp(@Autowired ApplicationEventPublisher publisher, @Autowired TransactionTemplate transaction) {
		this.rows = new EventPublicationRows(this.jdbc, publisher, transaction);
		this.rows.deleteAll();
	}

	@Test
	void listsExhaustedAndUnreadablePublicationsOldestFirst() {
		UUID unreadable = this.rows.insert(MISSING_EVENT, "listener-d", T2, "PUBLISHED", 1, null);
		UUID exhausted = this.rows.insert(PROBE_EVENT, "listener-a", T1, "FAILED", 6, T3);
		this.rows.insert(PROBE_EVENT, "listener-b", T1, "FAILED", 5, T3);
		this.rows.insert(PROBE_EVENT, "listener-c", T1, "RESUBMITTED", 6, T3);
		this.rows.insert(PROBE_EVENT, "listener-e", T1, "PUBLISHED", 1, T1);
		this.rows.insert(MISSING_EVENT, "listener-f", T1, null, 1, null);

		StrandedPublications listed = this.administration.strandedPublications();

		assertThat(listed.total()).isEqualTo(2);
		assertThat(listed.publications()).containsExactly(
				new StrandedPublication(exhausted, StrandedReason.EXHAUSTED, PROBE_EVENT, "listener-a", T1, 6, T3),
				new StrandedPublication(unreadable, StrandedReason.UNREADABLE, MISSING_EVENT, "listener-d", T2, 1,
						null));
	}

	@Test
	void listsPublicationsOfTheSamePublishedTimeInIdOrder() {
		UUID first = UUID.fromString("00000000-0000-4000-8000-000000000001");
		UUID second = UUID.fromString("00000000-0000-4000-8000-000000000002");
		this.rows.insert(second, PROBE_EVENT, "listener-a", T1, "FAILED", 6, T3);
		this.rows.insert(first, PROBE_EVENT, "listener-b", T1, "FAILED", 6, T3);

		assertThat(this.administration.strandedPublications().publications()).extracting(StrandedPublication::id)
			.containsExactly(first, second);
	}

	@Test
	void listsAtMostFiveHundredOfTheOldest() {
		this.jdbc.sql("""
				insert into event_publication
				  (id, listener_id, event_type, serialized_event, publication_date, status, completion_attempts)
				select gen_random_uuid(), 'listener', :eventType, '{}', timestamptz '2026-10-01 00:00:00+00' + n * interval '1 second',
				  'FAILED', 6
				from generate_series(1, 501) as n
				""").param("eventType", PROBE_EVENT).update();

		StrandedPublications listed = this.administration.strandedPublications();

		assertThat(listed.total()).isEqualTo(501);
		assertThat(listed.publications()).hasSize(500);
		assertThat(listed.publications().getFirst().publishedAt()).isEqualTo(Instant.parse("2026-10-01T00:00:01Z"));
		assertThat(listed.publications().getLast().publishedAt()).isEqualTo(Instant.parse("2026-10-01T00:08:20Z"));
	}

	@Test
	void resubmittedPublicationReachesTheListenerAndCompletes() {
		UUID id = exhaustedProbe(Behavior.FAIL_ONCE);

		assertThat(this.administration.resubmit(Set.of(id))).isEqualTo(
				new BatchResult.Processed<>(List.of(new ResubmissionResult(id, ResubmissionOutcome.RESUBMITTED))));

		await().atMost(TIMEOUT).until(() -> !this.rows.exists(id));
		assertThat(this.listener.calls(probeOf(id))).isEqualTo(2);
	}

	@Test
	void resubmittedPublicationThatFailsAgainStaysExhaustedWithOneMoreAttempt() {
		UUID id = exhaustedProbe(Behavior.ALWAYS_FAIL);

		this.administration.resubmit(Set.of(id));
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(this.rows.find(probeOf(id))).hasValueSatisfying((row) -> {
			assertThat(row.status()).isEqualTo("FAILED");
			assertThat(row.attempts()).isEqualTo(7);
		}));

		this.resubmission.resubmit();

		assertThat(this.rows.find(probeOf(id))).hasValueSatisfying((row) -> {
			assertThat(row.status()).isEqualTo("FAILED");
			assertThat(row.attempts()).isEqualTo(7);
		});
		assertThat(this.administration.strandedPublications().publications())
			.singleElement()
			.satisfies((publication) -> {
				assertThat(publication.reason()).isEqualTo(StrandedReason.EXHAUSTED);
				assertThat(publication.completionAttempts()).isEqualTo(7);
			});
	}

	@Test
	void resubmissionReportsWhyEachPublicationWasNotResubmitted() {
		UUID exhausted = exhaustedProbe(Behavior.ALWAYS_FAIL);
		UUID belowLimit = this.rows.insert(PROBE_EVENT, "listener-x", T1, "FAILED", 5, T3);
		UUID unreadable = this.rows.insert(MISSING_EVENT, "listener-x", T1, "FAILED", 6, T3);
		UUID missing = UUID.randomUUID();

		BatchResult<ResubmissionResult> result = this.administration
			.resubmit(Set.of(exhausted, belowLimit, unreadable, missing));

		assertThat(result).isEqualTo(new BatchResult.Processed<>(sortedBy(ResubmissionResult::id,
				new ResubmissionResult(exhausted, ResubmissionOutcome.RESUBMITTED),
				new ResubmissionResult(belowLimit, ResubmissionOutcome.NOT_EXHAUSTED),
				new ResubmissionResult(unreadable, ResubmissionOutcome.UNREADABLE),
				new ResubmissionResult(missing, ResubmissionOutcome.NOT_FOUND))));
		assertThat(attemptsOf(belowLimit)).isEqualTo(5);
		assertThat(attemptsOf(unreadable)).isEqualTo(6);
	}

	@Test
	void discardsStrandedPublicationsAndLogsEach(CapturedOutput output) {
		UUID exhausted = this.rows.insert(PROBE_EVENT, "listener-a", T1, "FAILED", 6, T3);
		UUID unreadable = this.rows.insert(MISSING_EVENT, "listener-d", T2, "PUBLISHED", 1, null);
		UUID unreadableWithoutAttempts = this.rows.insert(MISSING_EVENT, "listener-g", T2, "PUBLISHED", null, null);
		UUID belowLimit = this.rows.insert(PROBE_EVENT, "listener-b", T1, "FAILED", 5, T3);
		UUID missing = UUID.randomUUID();

		BatchResult<DiscardResult> result = this.administration
			.discard(Set.of(exhausted, unreadable, unreadableWithoutAttempts, belowLimit, missing));

		assertThat(result).isEqualTo(new BatchResult.Processed<>(sortedBy(DiscardResult::id,
				new DiscardResult(exhausted, DiscardOutcome.DISCARDED),
				new DiscardResult(unreadable, DiscardOutcome.DISCARDED),
				new DiscardResult(unreadableWithoutAttempts, DiscardOutcome.DISCARDED),
				new DiscardResult(belowLimit, DiscardOutcome.NOT_STRANDED),
				new DiscardResult(missing, DiscardOutcome.NOT_FOUND))));
		assertThat(this.rows.exists(exhausted)).isFalse();
		assertThat(this.rows.exists(unreadable)).isFalse();
		assertThat(this.rows.exists(unreadableWithoutAttempts)).isFalse();
		assertThat(this.rows.exists(belowLimit)).isTrue();
		assertThat(output).contains("운영자가 이벤트 발행을 폐기했다: id=" + exhausted
				+ ", reason=EXHAUSTED, eventType=net.yozmnews.platform.ResubmissionProbeEvent, listenerId=listener-a, publishedAt=2026-10-01T00:00:00Z, completionAttempts=6");
		assertThat(output).contains("운영자가 이벤트 발행을 폐기했다: id=" + unreadable
				+ ", reason=UNREADABLE, eventType=net.yozmnews.platform.NoSuchProbeEvent, listenerId=listener-d, publishedAt=2026-10-02T00:00:00Z, completionAttempts=1");
		assertThat(output).contains("운영자가 이벤트 발행을 폐기했다: id=" + unreadableWithoutAttempts
				+ ", reason=UNREADABLE, eventType=net.yozmnews.platform.NoSuchProbeEvent, listenerId=listener-g, publishedAt=2026-10-02T00:00:00Z, completionAttempts=0");
		assertThat(output).doesNotContain("id=" + belowLimit);
	}

	@Test
	void deletesOnlyWhatWasRead() {
		UUID id = this.rows.insert(PROBE_EVENT, "listener-a", T1, "FAILED", 6, T3);
		RegistryRow read = this.registryRows.findByIds(Set.of(id)).getFirst();
		this.rows.setAttempts(id, 7);

		assertThat(this.registryRows.deleteIfUnchanged(read)).isFalse();
		assertThat(this.rows.exists(id)).isTrue();

		RegistryRow reread = this.registryRows.findByIds(Set.of(id)).getFirst();
		assertThat(this.registryRows.deleteIfUnchanged(reread)).isTrue();
		assertThat(this.rows.exists(id)).isFalse();
	}

	@Test
	void refusesMoreThanFiveHundredPublicationsAtOnce() {
		UUID exhausted = this.rows.insert(PROBE_EVENT, "listener-a", T1, "FAILED", 6, T3);
		Set<UUID> fiveHundredOne = new HashSet<>(randomIds(500));
		fiveHundredOne.add(exhausted);

		assertThat(this.administration.resubmit(fiveHundredOne)).isEqualTo(new BatchResult.OverLimit<>(500));
		assertThat(this.administration.discard(fiveHundredOne)).isEqualTo(new BatchResult.OverLimit<>(500));
		assertThat(this.rows.exists(exhausted)).isTrue();
		assertThat(attemptsOf(exhausted)).isEqualTo(6);

		Set<UUID> fiveHundred = new HashSet<>(randomIds(500));
		BatchResult<ResubmissionResult> resubmittedWithinLimit = this.administration.resubmit(fiveHundred);
		assertThat(resubmittedWithinLimit).isInstanceOf(BatchResult.Processed.class);
		if (resubmittedWithinLimit instanceof BatchResult.Processed<ResubmissionResult> processed) {
			assertThat(processed.results()).hasSize(500);
		}
		BatchResult<DiscardResult> discardedWithinLimit = this.administration.discard(fiveHundred);
		assertThat(discardedWithinLimit).isInstanceOf(BatchResult.Processed.class);
		if (discardedWithinLimit instanceof BatchResult.Processed<DiscardResult> processed) {
			assertThat(processed.results()).hasSize(500);
		}
		assertThat(this.administration.resubmit(Set.of())).isEqualTo(new BatchResult.Processed<>(List.of()));
		assertThat(this.administration.discard(Set.of())).isEqualTo(new BatchResult.Processed<>(List.of()));
	}

	/** 시험 리스너가 처리에 실패한 발행을 만들고 처리 시도를 상한(6)으로 올린다 */
	private UUID exhaustedProbe(Behavior behavior) {
		String probe = UUID.randomUUID().toString();
		this.rows.publish(probe, behavior);
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(this.rows.find(probe))
			.hasValueSatisfying((row) -> assertThat(row.status()).isEqualTo("FAILED")));
		UUID id = this.rows.find(probe).orElseThrow().id();
		this.rows.setAttempts(id, 6);
		this.probes.put(id, probe);
		return id;
	}

	private String probeOf(UUID id) {
		return this.probes.get(id);
	}

	private int attemptsOf(UUID id) {
		return this.jdbc.sql("select completion_attempts from event_publication where id = :id")
			.param("id", id)
			.query(Integer.class)
			.single();
	}

	/** 결과는 요청 ID 의 UUID 순이다 */
	@SafeVarargs
	private static <T> List<T> sortedBy(Function<T, UUID> id, T... results) {
		return Arrays.stream(results).sorted(Comparator.comparing(id)).toList();
	}

	private static List<UUID> randomIds(int count) {
		return IntStream.range(0, count).mapToObj((i) -> UUID.randomUUID()).toList();
	}

}
