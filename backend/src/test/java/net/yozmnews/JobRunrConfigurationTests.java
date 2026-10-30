package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.jobrunr.dashboard.JobRunrDashboardWebServer;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.spring.autoconfigure.JobRunrProperties;
import org.jobrunr.utils.mapper.JsonMapper;
import org.jobrunr.utils.mapper.jackson3.Jackson3JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * JobRunr · Modulith 의 설정 — 실제 PostgreSQL 로 띄운 컨텍스트의 설정 값 · 빈 · 작업 테이블을 본다
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JobRunrConfigurationTests {

	@Autowired
	JobRunrProperties properties;

	@Autowired
	ApplicationContext context;

	@Autowired
	JdbcClient jdbc;

	@Test
	void librariesDoNotCreateSchema() {
		assertThat(this.properties.getDatabase().isSkipCreate()).isTrue();
		// Modulith 의 스키마 초기화 빈(패키지 전용 타입이라 이름으로 본다)
		assertThat(this.context.containsBean("databaseSchemaInitializer")).isFalse();
	}

	@Test
	void dashboardIsOffAndSendsNoUsageData() {
		assertThat(this.properties.getDashboard().isEnabled()).isFalse();
		assertThat(this.context.getBeanNamesForType(JobRunrDashboardWebServer.class)).isEmpty();
		assertThat(this.properties.getMiscellaneous().isAllowAnonymousDataUsage()).isFalse();
	}

	@Test
	void backgroundJobServerRunsWithJackson3Mapper() {
		assertThat(this.properties.getBackgroundJobServer().isEnabled()).isTrue();
		BackgroundJobServer server = this.context.getBean(BackgroundJobServer.class);
		await().atMost(Duration.ofSeconds(60)).until(server::isRunning);
		assertThat(this.context.getBean(JsonMapper.class)).isInstanceOf(Jackson3JsonMapper.class);
	}

	@Test
	void workerCountIsASettingRatherThanTheHostCoreCount() {
		assertThat(this.properties.getBackgroundJobServer().getWorkerCount()).isEqualTo(16);
		BackgroundJobServer server = this.context.getBean(BackgroundJobServer.class);
		await().atMost(Duration.ofSeconds(60)).until(server::isRunning);
		assertThat(server.getServerStatus().getWorkerPoolSize()).isEqualTo(16);
	}

	@Test
	void registersTheRecurringFeedPollingJob() {
		assertThat(this.jdbc.sql("select count(*) from jobrunr_recurring_jobs where id = 'poll-due-feeds'")
			.query(Integer.class)
			.single()).isEqualTo(1);
	}

	@Test
	void registersTheRecurringFeedHealthCheckJob() {
		assertThat(this.jdbc.sql("select count(*) from jobrunr_recurring_jobs where id = 'feed-health-check'")
			.query(Integer.class)
			.single()).isEqualTo(1);
	}

}
