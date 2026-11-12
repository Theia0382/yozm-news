package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;

import net.yozmnews.evaluation.ArticleTagging;
import net.yozmnews.evaluation.EventMembership;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.evaluation.TextEmbedding;
import net.yozmnews.evaluation.domain.embedder.ArticleEmbedder;
import net.yozmnews.platform.WebRole;
import net.yozmnews.platform.domain.ActiveRoles;
import net.yozmnews.tag.TagSetDirectory;
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
 * 지키는 규칙: platform-R8 · platform-R9 · tag-R2 · evaluation-R37 · evaluation-R46
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
		assertThat(RoleProbe.jobEntryPointBeans(this.context)).hasSize(9)
			.allSatisfy((type, beans) -> assertThat(beans).as(type.getName()).isZero());
		assertThat(RoleProbe.recurringJobIds(this.jdbc)).isEmpty();
		assertThat(this.context.containsBean("presetFeedReconciliation")).isFalse();
		// 스케줄링이 켜지지 않는다 — 이 역할에는 감시 기간이 없어 스케줄링이 켜져도 등록된 작업은 0건이므로 작업 수로는 가리지 못한다
		assertThat(this.context.getBeanNamesForType(ScheduledTaskHolder.class)).isEmpty();
	}

	@Test
	void seedsTheDefaultTagSetOnItsOwn() {
		// 이 컨텍스트는 새로 만든 빈 데이터베이스로 뜬다 — 화면 역할만 켠 프로세스도 기동 때 첫 버전을 적재한다(tag-R2)
		assertThat(this.context.getBean(TagSetDirectory.class).activeTagSet().number()).isEqualTo(1);
	}

	@Test
	void keepsTheMembershipJudgementApiLikeEveryApplicationService() {
		// 판정 공개 API 는 두 역할 공통이다 — 평가기는 부를 때 찾는다(evaluation-R37)
		assertThat(this.context.getBeanNamesForType(EventMembership.class)).hasSize(1);
	}

	@Test
	void keepsTheTaggingApiLikeEveryApplicationService() {
		// 태그 판정 공개 API 도 두 역할 공통이다 — 화면 역할의 평가 현황 · 검수가 판정 원본 조회를 평가기 없이 쓴다(evaluation-R42).
		// 평가기가 없을 때 판정을 거절하는 것은 ArticleTaggingServiceTests 가 본다
		assertThat(this.context.getBeanNamesForType(ArticleTagging.class)).hasSize(1);
		assertThat(this.context.getBean(ArticleTagging.class).find(List.of(new TagJudgementId(987_654_321)))).isEmpty();
	}

	@Test
	void loadsNoEmbedderButKeepsTheEmbeddingApi() {
		// 운영 임베더는 작업 역할에서만 만든다 — 화면 역할은 모델을 올리지 않는다. 임베딩 공개 API 는 두 역할 공통이고 임베더는 부를 때
		// 찾는다(evaluation-R46)
		assertThat(this.context.getBeanNamesForType(ArticleEmbedder.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(TextEmbedding.class)).hasSize(1);
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
		assertThat(this.context.getBeansOfType(SecurityFilterChain.class)).containsOnlyKeys("adminChain", "sessionChain",
				"defaultChain");
	}

}
