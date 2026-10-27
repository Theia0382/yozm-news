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

/** JobRunr · Modulith 의 기본 설정 — 스키마는 만들지 않고, 대시보드는 끄고, 작업 서버는 켠다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JobRunrConfigurationTests {

	@Autowired
	JobRunrProperties properties;

	@Autowired
	ApplicationContext context;

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

}
