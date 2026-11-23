package net.yozmnews.user.adapter.out.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.DigestCompleted;
import net.yozmnews.user.domain.summary.SummaryJobQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 정리 작업 등록 포트의 JobRunr 구현 — 실제 작업 저장소(PostgreSQL)에 (사용자, 다이제스트)마다 작업이 하나 생기고, 같은 짝을 거듭 등록해도
 * 하나다. 트랜잭션에서 낸 다이제스트 끝남 이벤트가 리스너를 지나 작업 등록까지 이어지는 경로도 본다 — 리스너는 커밋 뒤에 비동기로 돈다. 작업
 * 서버는 끈다 — 등록된 작업이 돌지 않는다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class JobRunrSummaryJobQueueTests {

	/** "user-storyline-summary:7:77" · "…:7:78" · "…:8:77" 의 이름 기반 UUID(버전 3) — 손으로 따로 계산한 값 */
	private static final UUID SEVEN_77 = UUID.fromString("67444fee-a340-3fe7-a8ba-2aa0cf3315b0");

	private static final UUID SEVEN_78 = UUID.fromString("3a5f75ec-634d-31dc-9a5e-6322f2dfdc5c");

	private static final UUID EIGHT_77 = UUID.fromString("4ff4cc12-9e25-3815-ac25-af0d49ba6118");

	/** "user-storyline-summary:9:99" · "…:9:100" 의 이름 기반 UUID — 손으로 따로 계산한 값 */
	private static final UUID NINE_99 = UUID.fromString("df1b632e-08bd-3149-b9cb-63b8468c8d36");

	private static final UUID NINE_100 = UUID.fromString("b9280a25-3ff4-3afe-af72-e515615f2207");

	@Autowired
	SummaryJobQueue queue;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from jobrunr_jobs where id in (:ids)")
			.param("ids", List.of(SEVEN_77.toString(), SEVEN_78.toString(), EIGHT_77.toString(), NINE_99.toString(),
					NINE_100.toString()))
			.update();
	}

	@Test
	void enqueuesOneJobPerUserAndDigest() {
		this.queue.enqueue(new UserId(7), 77);
		this.queue.enqueue(new UserId(7), 77);

		assertThat(jobs(SEVEN_77)).isEqualTo(1);
		assertThat(this.jdbc.sql("select jobsignature from jobrunr_jobs where id = :id")
			.param("id", SEVEN_77.toString())
			.query(String.class)
			.single()).contains("SummarizeStorylinesJob.run");
		assertThat(jobs(SEVEN_78) + jobs(EIGHT_77)).isZero();

		// 대조 — 다른 다이제스트 · 다른 사용자는 다른 작업이다
		this.queue.enqueue(new UserId(7), 78);
		this.queue.enqueue(new UserId(8), 77);
		assertThat(List.of(jobs(SEVEN_77), jobs(SEVEN_78), jobs(EIGHT_77))).containsExactly(1L, 1L, 1L);
	}

	@Test
	void aCompletedDigestPublishedInATransactionEnqueuesItsJobThroughTheListener() {
		LocalDate date = LocalDate.parse("2026-11-23");
		Instant completedAt = Instant.parse("2026-11-22T22:00:00Z");

		this.transaction.executeWithoutResult(
				status -> this.events.publishEvent(new DigestCompleted(new UserId(9), 99, date, completedAt)));

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(jobs(NINE_99)).isEqualTo(1));

		// 대조 — 트랜잭션이 되돌아가면 이벤트도 작업도 없다
		this.transaction.executeWithoutResult(status -> {
			this.events.publishEvent(new DigestCompleted(new UserId(9), 100, date, completedAt));
			status.setRollbackOnly();
		});
		await().during(Duration.ofSeconds(2))
			.atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(jobs(NINE_100)).isZero());
	}

	private long jobs(UUID id) {
		return this.jdbc.sql("select count(*) from jobrunr_jobs where id = :id")
			.param("id", id.toString())
			.query(Long.class)
			.single();
	}

}
