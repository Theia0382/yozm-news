package net.yozmnews.storyline.adapter.in.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.WorkerRole;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.storyline.application.LinkingRound;
import net.yozmnews.storyline.domain.queue.LinkingQueueRepository;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 잇기 대기 반복 작업 — 작업 역할에만 있고, 1분마다 · 재시도 없이 돌며, 회차를 부른다. 작업은 시험이 직접 만든다(작업 서버는 끈다).
 * <p>
 * 지키는 규칙: storyline-R3
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class LinkStorylinesJobTests {

	@Autowired
	LinkingRound round;

	@Autowired
	LinkingQueueRepository queue;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		StorylineRows.clear(this.jdbc);
	}

	@Test
	void theJobIsAWorkerRecurringJobWithoutRetries() throws NoSuchMethodException {
		Recurring recurring = LinkStorylinesJob.class.getMethod("run").getAnnotation(Recurring.class);
		Job job = LinkStorylinesJob.class.getMethod("run").getAnnotation(Job.class);

		assertThat(LinkStorylinesJob.class.isAnnotationPresent(WorkerRole.class)).isTrue();
		assertThat(recurring.id()).isEqualTo("storyline-linking");
		assertThat(recurring.interval()).isEqualTo("PT1M");
		assertThat(job.retries()).isZero();
	}

	@Test
	void runningTheJobRunsARound() {
		// 없는 사건 — 회차가 판정하고(지워진 사건, 소속 없음) 대기 행을 지운다
		this.queue.enqueue(999_999_998L, null, Instant.now());

		new LinkStorylinesJob(this.round).run();

		assertThat(this.jdbc.sql("select count(*) from storyline_linking_queue where event_id = 999999998")
			.query(Long.class)
			.single()).isZero();
	}

}
