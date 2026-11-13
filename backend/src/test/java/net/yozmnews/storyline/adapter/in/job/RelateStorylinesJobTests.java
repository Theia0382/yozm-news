package net.yozmnews.storyline.adapter.in.job;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.WorkerRole;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.storyline.application.RelationRound;
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
 * 관계 대기 반복 작업 — 작업 역할에만 있고, 1분마다 · 재시도 없이 잇기 작업과 다른 ID 로 돌며, 회차를 부른다. 작업은 시험이 직접
 * 만든다(작업 서버는 끈다).
 * <p>
 * 지키는 규칙: storyline-R31
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class RelateStorylinesJobTests {

	@Autowired
	RelationRound round;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		StorylineRows.clear(this.jdbc);
	}

	@Test
	void theJobIsAWorkerRecurringJobWithoutRetries() throws NoSuchMethodException {
		Recurring recurring = RelateStorylinesJob.class.getMethod("run").getAnnotation(Recurring.class);
		Job job = RelateStorylinesJob.class.getMethod("run").getAnnotation(Job.class);

		assertThat(RelateStorylinesJob.class.isAnnotationPresent(WorkerRole.class)).isTrue();
		assertThat(recurring.id()).isEqualTo("storyline-relation");
		assertThat(recurring.id()).isNotEqualTo(LinkStorylinesJob.ID);
		assertThat(recurring.interval()).isEqualTo("PT1M");
		assertThat(job.retries()).isZero();
	}

	@Test
	void runningTheJobRunsARound() {
		// 구성 사건이 없는 스토리라인 — 회차가 후보 없이 대기 행을 지운다(결선된 관계 판정은 부르지 않는다)
		long storyline = this.jdbc.sql("""
				insert into storylines (model_version, centroid, article_count, first_article_at, last_article_at,
				    representative_event_id, created_at, updated_at)
				values ('relation-job', cast('[1,0]' as halfvec), 1, now(), now(), 1, now(), now())
				returning id
				""").query(Long.class).single();
		this.jdbc.sql("insert into storyline_relation_queue (storyline_id, enqueued_at) values (:id, now())")
			.param("id", storyline)
			.update();

		new RelateStorylinesJob(this.round).run();

		assertThat(this.jdbc.sql("select count(*) from storyline_relation_queue where storyline_id = :id")
			.param("id", storyline)
			.query(Long.class)
			.single()).isZero();
	}

}
