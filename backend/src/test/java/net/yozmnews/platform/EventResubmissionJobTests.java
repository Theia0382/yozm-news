package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.ResubmissionProbeEvent.Behavior;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제 JobRunr 반복 작업의 재처리 — 반복 주기와 작업 서버 poll 간격을 JobRunr 하한(5초)으로 줄여 기다린다. 재처리 정책의
 * 조건((6 − 1) × 5초가 30분보다 짧다)은 그대로 성립한다.
 * <p>
 * 지키는 규칙: platform-R1 · platform-R5
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = { "yozmnews.events.resubmission-interval=PT5S",
		"jobrunr.background-job-server.poll-interval-in-seconds=5" })
class EventResubmissionJobTests {

	private static final Duration TIMEOUT = Duration.ofSeconds(60);

	private static final String RECURRING_JOB_ID = "event-publication-resubmission";

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ResubmissionProbeListener listener;

	EventPublicationRows rows;

	@BeforeEach
	void setUp(@Autowired ApplicationEventPublisher publisher, @Autowired TransactionTemplate transaction) {
		this.rows = new EventPublicationRows(this.jdbc, publisher, transaction);
		this.rows.deleteAll();
	}

	@Test
	void failedEventIsResubmittedByRecurringJob() {
		String probe = UUID.randomUUID().toString();
		this.rows.publish(probe, Behavior.FAIL_ONCE);

		await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(50)).untilAsserted(() -> assertThat(this.rows.find(probe))
			.hasValueSatisfying((row) -> {
				assertThat(row.status()).isEqualTo("FAILED");
				assertThat(row.attempts()).isEqualTo(1);
				assertThat(row.completionDate()).isNull();
			}));
		// 실패 행이 보일 때 이미 끝난 회차는 재처리했을 수 없다
		Set<String> finishedBeforeFailure = jobStates().entrySet()
			.stream()
			.filter((job) -> job.getValue().equals("SUCCEEDED"))
			.map(Map.Entry::getKey)
			.collect(Collectors.toSet());
		await().atMost(TIMEOUT).until(() -> this.rows.find(probe).isEmpty());
		// 행을 지운 회차가 여기 든다 — 작업 메서드가 돌아오기 전이면 아직 PROCESSING 이다. 그 뒤에 만들어진 다음 회차도 들 수 있다
		Set<String> resubmitting = new HashSet<>(jobStates().keySet());
		resubmitting.removeAll(finishedBeforeFailure);

		assertThat(this.listener.calls(probe)).isEqualTo(2);
		assertThat(this.jdbc.sql("select count(*) from jobrunr_recurring_jobs where id = :id")
			.param("id", RECURRING_JOB_ID)
			.query(Integer.class)
			.single()).isEqualTo(1);
		assertThat(resubmitting).isNotEmpty();
		await().atMost(TIMEOUT).untilAsserted(() -> {
			Map<String, String> states = jobStates();
			assertThat(resubmitting).allSatisfy((job) -> assertThat(states.get(job)).isEqualTo("SUCCEEDED"));
		});
	}

	@Test
	void successfulEventIsCompletedOnFirstAttempt() {
		String probe = UUID.randomUUID().toString();
		this.rows.publish(probe, Behavior.SUCCEED);

		await().atMost(TIMEOUT).until(() -> this.listener.calls(probe) == 1 && this.rows.find(probe).isEmpty());
	}

	/** 재처리 반복 작업이 만든 회차마다 작업 ID 와 상태를 읽는다 */
	private Map<String, String> jobStates() {
		return this.jdbc.sql("select id, state from jobrunr_jobs where recurringjobid = :id")
			.param("id", RECURRING_JOB_ID)
			.query((rs, rowNum) -> Map.entry(rs.getString("id"), rs.getString("state")))
			.list()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

}
