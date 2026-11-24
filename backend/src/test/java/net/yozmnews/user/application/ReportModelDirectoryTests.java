package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.application.ProvisionedAccounts;
import net.yozmnews.account.domain.account.Onboarding;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.AccountEvents;
import net.yozmnews.user.DigestModelDirectory;
import net.yozmnews.user.ReportModel;
import net.yozmnews.user.ReportModelDirectory;
import net.yozmnews.user.UserTables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 리포트의 쓸 모델과 추론 강도 — 쓸 모델은 다이제스트와 같은 판정이고, 추론 강도는 설정 행의 값이다. 실제 PostgreSQL.
 * <p>
 * 모델 ID 는 기본 허용 목록(`llm-models/default.yaml`)의 손으로 쓴 리터럴이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ReportModelDirectoryTests {

	private static final Instant NOW = Instant.parse("2026-11-25T03:00:00Z");

	@Autowired
	ReportModelDirectory directory;

	@Autowired
	DigestModelDirectory digestModels;

	@Autowired
	ApplicationContext context;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from llm_credentials where user_id in (select id from users where email like 'report-model-%')")
			.update();
		AccountEvents.awaitCreatedHandled(this.jdbc);
		UserTables.deleteByEmail(this.jdbc, "report-model-%");
	}

	@Test
	void theChosenModelWithTheReasoningEffortOfTheSettingsRow() {
		UserId user = account("report-model-chosen@example.com");
		key(user, "anthropic", "VALID");
		this.jdbc.sql("""
				update user_settings set llm_provider = 'anthropic', llm_model = 'claude-opus-5-5', reasoning_effort = 'HIGH'
				where user_id = :user""").param("user", user.value()).update();

		assertThat(this.directory.modelOf(user)).hasValueSatisfying(model -> {
			assertThat(model.model().id()).isEqualTo("claude-opus-5-5");
			assertThat(model.effort()).isEqualTo(ReasoningEffort.HIGH);
		});
		// 대조 — 다이제스트의 쓸 모델과 같은 판정이다
		assertThat(this.digestModels.modelOf(user)).map(LlmModel::id).contains("claude-opus-5-5");
	}

	@Test
	void theDefaultsWhenTheSettingsRowIsMissing() {
		UserId user = account("report-model-unset@example.com");
		key(user, "anthropic", "VALID");
		this.jdbc.sql("delete from user_settings where user_id = :id").param("id", user.value()).update();

		assertThat(this.directory.modelOf(user)).map(ReportModel::effort).contains(ReasoningEffort.MEDIUM);
		assertThat(this.directory.modelOf(user)).map(model -> model.model().id()).contains("claude-sonnet-5-5");
	}

	@Test
	void emptyWithoutAValidKeyOrForAnAccountThatIsNotActive() {
		UserId keyless = account("report-model-keyless@example.com");
		UserId suspended = account("report-model-suspended@example.com");
		key(suspended, "openai", "VALID");
		this.jdbc.sql("update users set status = 'SUSPENDED' where id = :id").param("id", suspended.value()).update();
		UserId active = account("report-model-active@example.com");
		key(active, "openai", "VALID");

		assertThat(this.directory.modelOf(keyless)).isEmpty();
		assertThat(this.directory.modelOf(suspended)).isEmpty();
		assertThat(this.directory.modelOf(new UserId(Long.MAX_VALUE))).isEmpty();
		// 대조 — 같은 키의 쓰는 계정은 모델이 있다
		assertThat(this.directory.modelOf(active)).map(model -> model.model().id()).contains("gpt-6.1-terra");
	}

	private UserId account(String email) {
		UserId user = ProvisionedAccounts.provision(this.context, Onboarding.OPEN_SIGN_UP, email, "사용자",
				"correct horse battery", NOW).orElseThrow();
		AccountEvents.awaitCreatedHandled(this.jdbc);
		return user;
	}

	private void key(UserId user, String provider, String status) {
		this.jdbc.sql("""
				insert into llm_credentials (user_id, provider, ciphertext, encrypted_dek, kek_version, key_hint, status,
				  validated_at, created_at)
				values (:user, :provider, '\\x01'::bytea, '\\x01'::bytea, 1, '', :status, now(), now())
				""").param("user", user.value()).param("provider", provider).param("status", status).update();
	}

}
