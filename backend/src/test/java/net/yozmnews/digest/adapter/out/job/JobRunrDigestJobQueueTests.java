package net.yozmnews.digest.adapter.out.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.digest.domain.digest.DigestJobQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 생성 작업 등록 포트의 JobRunr 구현 — 실제 작업 저장소(PostgreSQL)에 토큰 ID 의 작업이 하나 생기고, 같은 토큰을 거듭 등록해도 하나다. 작업
 * 서버는 끈다 — 등록된 작업이 돌지 않는다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class JobRunrDigestJobQueueTests {

	private static final UUID TOKEN = UUID.fromString("3c1f7a52-8e0d-4b96-a2c7-5d9e0f1b6a48");

	private static final UUID OTHER = UUID.fromString("e8a24b07-1c6f-4d3a-9b85-f02d7c6e1a93");

	@Autowired
	DigestJobQueue queue;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from jobrunr_jobs where id in (:ids)")
			.param("ids", java.util.List.of(TOKEN.toString(), OTHER.toString()))
			.update();
	}

	@Test
	void enqueuesOneJobPerToken() {
		this.queue.enqueue(new DigestId(77), TOKEN);
		this.queue.enqueue(new DigestId(77), TOKEN);

		assertThat(jobs(TOKEN)).isEqualTo(1);
		assertThat(this.jdbc.sql("select jobsignature from jobrunr_jobs where id = :id")
			.param("id", TOKEN.toString())
			.query(String.class)
			.single()).contains("GenerateDigestJob.run");

		// 대조 — 다른 토큰은 다른 작업이다(다시 등록)
		this.queue.enqueue(new DigestId(77), OTHER);
		assertThat(jobs(OTHER)).isEqualTo(1);
	}

	private long jobs(UUID token) {
		return this.jdbc.sql("select count(*) from jobrunr_jobs where id = :id")
			.param("id", token.toString())
			.query(Long.class)
			.single();
	}

}
