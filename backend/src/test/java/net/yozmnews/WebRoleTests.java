package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;

import net.yozmnews.platform.WebRole;
import net.yozmnews.platform.domain.ActiveRoles;
import org.jobrunr.scheduling.JobScheduler;
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
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

/**
 * 화면 역할만 켠 프로세스 — 실제 PostgreSQL 로 띄운 앱의 구성요소와 HTTP 응답을 본다.
 * <p>
 * 지키는 규칙: platform-R8 · platform-R9
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(WebRole.PROFILE)
class WebRoleTests {

	@LocalServerPort
	int port;

	@Autowired
	ApplicationContext context;

	@Autowired
	JdbcClient jdbc;

	@Test
	void takesOnlyTheWebRole() {
		assertThat(this.context.getBean(ActiveRoles.class)).isEqualTo(new ActiveRoles(true, false));
	}

	@Test
	void runsNoBackgroundJobs() {
		assertThat(this.context.getBeanNamesForType(BackgroundJobServer.class)).isEmpty();
		assertThat(RoleProbe.jobEntryPointBeans(this.context)).hasSize(4)
			.allSatisfy((type, beans) -> assertThat(beans).as(type.getName()).isZero());
		assertThat(RoleProbe.recurringJobIds(this.jdbc)).isEmpty();
		assertThat(this.context.containsBean("presetFeedReconciliation")).isFalse();
		// 스케줄링이 켜지지 않는다 — 이 역할에는 감시 기간이 없어 스케줄링이 켜져도 등록된 작업은 0건이므로 작업 수로는 가리지 못한다
		assertThat(this.context.getBeanNamesForType(ScheduledTaskHolder.class)).isEmpty();
	}

	@Test
	void canStillEnqueueJobs() {
		assertThat(this.context.getBeanNamesForType(JobScheduler.class)).hasSize(1);
	}

	@Test
	void servesTheScreenApiAndHealth() throws Exception {
		HttpResponse<String> ping = RoleProbe.get(this.port, "/api/ping");
		assertThat(ping.statusCode()).isEqualTo(200);
		JSONAssert.assertEquals("""
				{"message":"pong"}
				""", ping.body(), JSONCompareMode.STRICT);

		HttpResponse<String> health = RoleProbe.get(this.port, "/actuator/health");
		assertThat(health.statusCode()).isEqualTo(200);
		JSONAssert.assertEquals("""
				{"groups":["liveness","readiness"],"status":"UP"}
				""", health.body(), JSONCompareMode.STRICT);
	}

	@Test
	void closesTheAdminPathsWithoutATokenAndCreatesNoDefaultUser() throws Exception {
		HttpResponse<String> admin = RoleProbe.get(this.port, "/api/admin/sources");
		assertThat(admin.statusCode()).isEqualTo(403);
		assertThat(admin.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("application/problem+json"));
		assertThat(this.context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
		assertThat(this.context.getBeansOfType(SecurityFilterChain.class)).containsOnlyKeys("adminChain", "defaultChain");
	}

}
