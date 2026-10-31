package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import net.yozmnews.platform.WorkerRole;
import net.yozmnews.platform.domain.ActiveRoles;
import org.jobrunr.server.BackgroundJobServer;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.test.context.ActiveProfiles;

/**
 * 작업 역할만 켠 프로세스 — 실제 PostgreSQL 로 띄운 앱의 구성요소와 HTTP 응답을 본다.
 * <p>
 * 지키는 규칙: platform-R2 · platform-R8 · platform-R9
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(WorkerRole.PROFILE)
class WorkerRoleTests {

	@LocalServerPort
	int port;

	@Autowired
	ApplicationContext context;

	@Autowired
	JdbcClient jdbc;

	@Test
	void takesOnlyTheWorkerRole() {
		assertThat(this.context.getBean(ActiveRoles.class)).isEqualTo(new ActiveRoles(false, true));
	}

	@Test
	void runsTheJobServerWithEveryJobEntryPoint() {
		BackgroundJobServer server = this.context.getBean(BackgroundJobServer.class);
		await().atMost(Duration.ofSeconds(60)).until(server::isRunning);
		assertThat(RoleProbe.jobEntryPointBeans(this.context)).hasSize(4)
			.allSatisfy((type, beans) -> assertThat(beans).as(type.getName()).isEqualTo(1));
		assertThat(RoleProbe.recurringJobIds(this.jdbc)).containsExactlyInAnyOrder("poll-due-feeds",
				"feed-health-check", "event-publication-resubmission");
	}

	@Test
	void reconcilesPresetFeedsOnStartup() {
		assertThat(this.context.containsBean("presetFeedReconciliation")).isTrue();
	}

	@Test
	void monitorsStalePublicationsEveryMinute() {
		List<ScheduledTask> monitors = RoleProbe.stalenessMonitorTasks(this.context);

		assertThat(monitors).hasSize(1);
		assertThat(monitors.getFirst().getTask()).isInstanceOfSatisfying(FixedDelayTask.class,
				task -> assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofMinutes(1)));
	}

	@Test
	void doesNotServeTheScreenApiButServesHealth() throws Exception {
		assertThat(RoleProbe.get(this.port, "/api/ping").statusCode()).isEqualTo(404);

		HttpResponse<String> health = RoleProbe.get(this.port, "/actuator/health");
		assertThat(health.statusCode()).isEqualTo(200);
		JSONAssert.assertEquals("""
				{"groups":["liveness","readiness"],"status":"UP"}
				""", health.body(), JSONCompareMode.STRICT);
	}

}
