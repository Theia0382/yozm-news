package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

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
 * 실패한 이벤트 발행을 실제 JobRunr 반복 작업이 다시 처리한다. 반복 주기와 작업 서버 poll 간격을 JobRunr 하한(5초)으로
 * 줄여 기다린다. 재처리 정책의 조건((6 − 1) × 5초가 30분보다 짧다)은 그대로 성립한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = { "yozmnews.events.resubmission-interval=PT5S",
		"jobrunr.background-job-server.poll-interval-in-seconds=5" })
class EventResubmissionJobTests {

	private static final Duration TIMEOUT = Duration.ofSeconds(60);

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
		await().atMost(TIMEOUT).until(() -> this.rows.find(probe).isEmpty());

		assertThat(this.listener.calls(probe)).isEqualTo(2);
		assertThat(this.jdbc.sql("select count(*) from jobrunr_recurring_jobs where id = 'event-publication-resubmission'")
			.query(Integer.class)
			.single()).isEqualTo(1);
		assertThat(this.jdbc.sql("""
				select count(*) from jobrunr_jobs
				where recurringjobid = 'event-publication-resubmission' and state = 'SUCCEEDED'
				""").query(Integer.class).single()).isPositive();
	}

	@Test
	void successfulEventIsCompletedOnFirstAttempt() {
		String probe = UUID.randomUUID().toString();
		this.rows.publish(probe, Behavior.SUCCEED);

		await().atMost(TIMEOUT).until(() -> this.listener.calls(probe) == 1 && this.rows.find(probe).isEmpty());
	}

}
