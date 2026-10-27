package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.ResubmissionProbeEvent.Behavior;
import net.yozmnews.platform.application.EventResubmissionService;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재처리 대상 선별 · 상한 · 멈춘 발행. 반복 작업이 끼어들지 않도록 작업 서버를 끄고 {@code resubmit()} 을 직접 불러
 * 판정한다(반복 작업 등록과 Modulith staleness 감시는 그대로 돈다). 감시 주기는 2초로 줄이고 멈춤 기준은 운영 값(30분)을
 * 쓴다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = { "jobrunr.background-job-server.enabled=false",
		"spring.modulith.events.staleness.check-interval=PT2S" })
@ExtendWith(OutputCaptureExtension.class)
class EventResubmissionTests {

	private static final Duration TIMEOUT = Duration.ofSeconds(60);

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ResubmissionProbeListener listener;

	@Autowired
	EventResubmissionService service;

	EventPublicationRows rows;

	@BeforeEach
	void setUp(@Autowired ApplicationEventPublisher publisher, @Autowired TransactionTemplate transaction) {
		this.rows = new EventPublicationRows(this.jdbc, publisher, transaction);
		this.rows.deleteAll();
	}

	@Test
	void exhaustedPublicationsStayFailedAndAreReported(CapturedOutput output) {
		String exhausted = UUID.randomUUID().toString();
		this.rows.publish(exhausted, Behavior.ALWAYS_FAIL);
		awaitFailed(exhausted, 1);
		for (int attempts = 2; attempts <= 6; attempts++) {
			this.service.resubmit();
			awaitFailed(exhausted, attempts);
		}
		UUID exhaustedId = this.rows.find(exhausted).orElseThrow().id();

		// 상한 바로 아래인 발행은 같은 회차에 재제출된다
		String belowLimit = UUID.randomUUID().toString();
		this.rows.publish(belowLimit, Behavior.ALWAYS_FAIL);
		awaitFailed(belowLimit, 1);
		this.jdbc.sql("update event_publication set completion_attempts = 5 where id = :id")
			.param("id", this.rows.find(belowLimit).orElseThrow().id())
			.update();

		this.service.resubmit();
		awaitFailed(belowLimit, 6);

		assertThat(this.rows.find(exhausted)).hasValueSatisfying((row) -> {
			assertThat(row.status()).isEqualTo("FAILED");
			assertThat(row.attempts()).isEqualTo(6);
		});
		assertThat(this.listener.calls(exhausted)).isEqualTo(6);
		assertThat(this.listener.calls(belowLimit)).isEqualTo(2);
		assertThat(output).contains("재처리 상한(6회)에 이른 실패 이벤트 발행 1건: [" + exhaustedId + "]");
	}

	@Test
	void exhaustedRowsDoNotStarveNewFailures() {
		String template = UUID.randomUUID().toString();
		this.rows.publish(template, Behavior.ALWAYS_FAIL);
		awaitFailed(template, 1);
		UUID templateId = this.rows.find(template).orElseThrow().id();
		// 상한에 이른 실패 행 102개 — 새 실패보다 먼저 발행됐고 내용이 서로 다르다
		this.jdbc.sql("""
				update event_publication
				set completion_attempts = 6, publication_date = publication_date - interval '1 hour'
				where id = :id
				""").param("id", templateId).update();
		this.jdbc.sql("""
				insert into event_publication
				  (id, listener_id, event_type, serialized_event, publication_date, status, completion_attempts)
				select gen_random_uuid(), listener_id, event_type, replace(serialized_event, :template, 'starved-' || n),
				  publication_date - n * interval '1 second', 'FAILED', 6
				from event_publication, generate_series(1, 101) as n
				where id = :id
				""").param("template", template).param("id", templateId).update();

		String fresh = UUID.randomUUID().toString();
		this.rows.publish(fresh, Behavior.FAIL_ONCE);
		awaitFailed(fresh, 1);

		this.service.resubmit();

		await().atMost(TIMEOUT).until(() -> this.rows.find(fresh).isEmpty());
		assertThat(this.listener.calls(fresh)).isEqualTo(2);
		assertThat(this.jdbc
			.sql("select count(*) from event_publication where status = 'FAILED' and completion_attempts = 6")
			.query(Integer.class)
			.single()).isEqualTo(102);
	}

	@Test
	void stalledPublicationsAreFailedByMonitorAndResubmitted() {
		String template = UUID.randomUUID().toString();
		this.rows.publish(template, Behavior.ALWAYS_FAIL);
		awaitFailed(template, 1);
		UUID templateId = this.rows.find(template).orElseThrow().id();

		// 상태 셋 × 발행 시각 둘(멈춤 기준 30분의 양쪽). 다시 처리되면 성공하는 내용이다
		List<String> statuses = List.of("PUBLISHED", "PROCESSING", "RESUBMITTED");
		Map<String, String> older = new LinkedHashMap<>();
		Map<String, String> newer = new LinkedHashMap<>();
		for (String status : statuses) {
			older.put(status, insertStalled(templateId, template, status, 31));
			newer.put(status, insertStalled(templateId, template, status, 29));
		}
		this.jdbc.sql("delete from event_publication where id = :id").param("id", templateId).update();

		await().atMost(TIMEOUT)
			.untilAsserted(() -> older.values()
				.forEach((probe) -> assertThat(this.rows.find(probe)).as(probe)
					.hasValueSatisfying((row) -> assertThat(row.status()).isEqualTo("FAILED"))));
		// 감시가 두 번 넘게 도는 동안(주기 2초) 기준 안쪽 발행은 그대로다
		await().during(Duration.ofSeconds(5))
			.atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> newer.forEach((status, probe) -> assertThat(this.rows.find(probe)).as(probe)
				.hasValueSatisfying((row) -> assertThat(row.status()).isEqualTo(status))));

		this.service.resubmit();

		await().atMost(TIMEOUT)
			.untilAsserted(() -> older.values()
				.forEach((probe) -> assertThat(this.rows.find(probe)).as(probe).isEmpty()));
		older.values().forEach((probe) -> assertThat(this.listener.calls(probe)).as(probe).isEqualTo(1));
		newer.forEach((status, probe) -> assertThat(this.rows.find(probe)).as(probe)
			.hasValueSatisfying((row) -> assertThat(row.status()).isEqualTo(status)));
	}

	/** 견본 행을 복제해 {@code minutesAgo} 분 전에 발행돼 {@code status} 에 머문 행을 만든다. 내용의 식별자를 돌려준다. */
	private String insertStalled(UUID templateId, String template, String status, int minutesAgo) {
		String probe = "stalled-" + status + "-" + minutesAgo + "-" + UUID.randomUUID();
		this.jdbc.sql("""
				insert into event_publication
				  (id, listener_id, event_type, serialized_event, publication_date, status, completion_attempts)
				select gen_random_uuid(), listener_id, event_type,
				  replace(replace(serialized_event, :template, :probe), 'ALWAYS_FAIL', 'SUCCEED'),
				  now() - make_interval(mins => :minutes), :status, 1
				from event_publication
				where id = :id
				""")
			.param("template", template)
			.param("probe", probe)
			.param("minutes", minutesAgo)
			.param("status", status)
			.param("id", templateId)
			.update();
		return probe;
	}

	private void awaitFailed(String probe, int attempts) {
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(this.rows.find(probe)).hasValueSatisfying((row) -> {
			assertThat(row.status()).isEqualTo("FAILED");
			assertThat(row.attempts()).isEqualTo(attempts);
			assertThat(row.completionDate()).isNull();
		}));
	}

}
