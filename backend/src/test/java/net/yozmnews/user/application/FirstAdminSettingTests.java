package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.user.domain.bootstrap.FirstAdminDecision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 첫 운영자 설정의 결선 — 앱의 첫 운영자 처리는 설정 {@code yozmnews.first-admin.email} · {@code password} 를 읽는다.
 * <p>
 * 지키는 규칙: user-R34
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "jobrunr.background-job-server.enabled=false",
		"yozmnews.first-admin.email=setting@example.org", "yozmnews.first-admin.password=initial horse battery" })
class FirstAdminSettingTests {

	@Autowired
	FirstAdminBootstrap bootstrap;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from users").update();
	}

	@Test
	void theAppsFirstAdminReadsTheSetting() {
		assertThat(this.bootstrap.run()).isInstanceOf(FirstAdminDecision.Create.class);

		assertThat(this.jdbc.sql("select email from users where role = 'ADMIN'").query(String.class).single())
			.isEqualTo("setting@example.org");
	}

	@Test
	void deploymentEnvironmentVariablesBindToTheSetting() {
		// 배포 구성이 넘기는 환경 변수 이름 그대로 — 운영체제 환경 변수 원천의 이름 규칙으로 설정에 묶인다
		SystemEnvironmentPropertySource environment = new SystemEnvironmentPropertySource(
				StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of("YOZMNEWS_FIRST_ADMIN_EMAIL",
						"env@example.org", "YOZMNEWS_FIRST_ADMIN_PASSWORD", "env horse battery"));

		FirstAdminProperties bound = new Binder(ConfigurationPropertySources.from(environment))
			.bind("yozmnews.first-admin", FirstAdminProperties.class)
			.get();

		assertThat(bound.email()).isEqualTo("env@example.org");
		assertThat(bound.password()).isEqualTo("env horse battery");
	}

}
