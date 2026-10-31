package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import net.yozmnews.platform.domain.ActiveRoles;
import org.jobrunr.server.BackgroundJobServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 역할 profile 을 켜지 않은 프로세스 — 기본 profile 로 두 역할을 함께 한다. HealthEndpointTests 와 같은 구성이라 컨텍스트를
 * 함께 쓴다.
 * <p>
 * 지키는 규칙: platform-R8 · platform-R9
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BothRolesTests {

	@LocalServerPort
	int port;

	@Autowired
	ApplicationContext context;

	@Autowired
	Environment environment;

	@Autowired
	JdbcClient jdbc;

	@Test
	void takesBothRolesFromTheDefaultProfiles() {
		assertThat(this.environment.getActiveProfiles()).isEmpty();
		assertThat(this.environment.getDefaultProfiles()).containsExactly("web", "worker");
		assertThat(this.context.getBean(ActiveRoles.class)).isEqualTo(new ActiveRoles(true, true));
	}

	@Test
	void runsBackgroundJobs() {
		BackgroundJobServer server = this.context.getBean(BackgroundJobServer.class);
		await().atMost(Duration.ofSeconds(60)).until(server::isRunning);
		assertThat(RoleProbe.jobEntryPointBeans(this.context)).hasSize(4)
			.allSatisfy((type, beans) -> assertThat(beans).as(type.getName()).isEqualTo(1));
		assertThat(RoleProbe.recurringJobIds(this.jdbc)).containsExactlyInAnyOrder("poll-due-feeds",
				"feed-health-check", "event-publication-resubmission");
		assertThat(this.context.containsBean("presetFeedReconciliation")).isTrue();
		assertThat(RoleProbe.stalenessMonitorTasks(this.context)).hasSize(1);
	}

	@Test
	void runsNoTimeEvents() {
		// 스케줄링은 작업 역할 구성이 켜고, Modulith 의 시간 이벤트는 쓰지 않는다
		assertThat(this.context.containsBean("moments")).isFalse();
		assertThat(RoleProbe.scheduledTasks(this.context)).hasSize(1);
	}

	@Test
	void servesTheScreenApi() throws Exception {
		assertThat(RoleProbe.get(this.port, "/api/ping").statusCode()).isEqualTo(200);
	}

}
