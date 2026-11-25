package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.InstantSource;

import net.yozmnews.evaluation.adapter.out.jev.JevArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.platform.AppClock;
import net.yozmnews.platform.MeasurementClockAdministration;
import net.yozmnews.platform.MeasurementClockStatus;
import net.yozmnews.platform.application.SystemClock;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;

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
		assertThat(RoleProbe.jobEntryPointBeans(this.context)).hasSize(18)
			.allSatisfy((type, beans) -> assertThat(beans).as(type.getName()).isEqualTo(1));
		assertThat(RoleProbe.recurringJobIds(this.jdbc)).containsExactlyInAnyOrder("poll-due-feeds",
				"feed-health-check", "event-publication-resubmission", "evaluate-pending-articles",
				"backfill-missing-tags", "event-intake", "event-cleanup", "storyline-linking", "storyline-relation",
				"expire-sessions", "forget-login-failures", "forget-read-notices", "rewrap-llm-keys",
				"forget-request-counts", "digest-candidate-cleanup", "schedule-due-digests",
				"recover-stalled-digests", "schedule-due-reports");
		assertThat(this.context.containsBean("presetFeedReconciliation")).isTrue();
		assertThat(RoleProbe.stalenessMonitorTasks(this.context)).hasSize(1);
	}

	@Test
	void evaluatesWithJevWithoutReachingTheRealService() {
		// 시험 설정은 키와 닿지 않는 기본 주소를 주고, 모델은 main 의 기본값을 쓴다
		assertThat(this.context.getBean(ArticleEvaluator.class)).isInstanceOf(JevArticleEvaluator.class);
		assertThat(this.environment.getProperty("yozmnews.jev.base-url")).isEqualTo("http://127.0.0.1:9");
		assertThat(this.environment.getProperty("yozmnews.jev.model")).isEqualTo("jev-latest");
	}

	@Test
	void readsTheTimeFromOneAppClockThatIsNotAJavaTimeClock() {
		// Spring Modulith 는 java.time.Clock bean 을 이벤트 발행 레지스트리에 집어 쓴다 — 앱의 시계는 그 타입이 아니다
		assertThat(this.context.getBeansOfType(AppClock.class)).hasSize(1);
		assertThat(this.context.getBeansOfType(InstantSource.class)).hasSize(1);
		assertThat(this.context.getBeansOfType(Clock.class)).isEmpty();
		// 측정 profile 이 아니면 실제 시간의 시계이고, 측정용 시계 운영 API 는 Off 다
		assertThat(this.context.getBean(AppClock.class)).isInstanceOf(SystemClock.class);
		assertThat(this.context.getBean(MeasurementClockAdministration.class).status())
			.isInstanceOf(MeasurementClockStatus.Off.class);
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

	@Test
	void adminPathsNeedASignInAndNoDefaultUserExists() throws Exception {
		HttpResponse<String> admin = RoleProbe.get(this.port, "/api/admin/sources");
		assertThat(admin.statusCode()).isEqualTo(401);
		assertThat(admin.body()).contains("\"type\":\"urn:yozmnews:problem:unauthenticated\"");
		assertThat(admin.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("application/problem+json"));
		assertThat(this.context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
		assertThat(this.context.getBeansOfType(SecurityFilterChain.class)).containsOnlyKeys("adminChain", "sessionChain",
				"defaultChain");
	}

}
