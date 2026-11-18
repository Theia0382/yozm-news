package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.DigestModelDirectory;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.account.Onboarding;
import net.yozmnews.user.domain.credential.PasswordPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 다이제스트가 읽는 쓸 모델 — 고른 모델을 쓸 수 있으면 그것, 아니면 쓸 수 있는 키가 있는 공급자의 첫 허용 모델, 그것도 없으면 빈 값. 정지 ·
 * 탈퇴 · 없는 계정은 빈 값. 설정 행이 없으면 고른 모델이 없는 것으로 본다. 실제 PostgreSQL 과 기본 허용 모델 목록
 * (`llm-models/default.yaml`).
 * <p>
 * 기대 모델은 기본 목록 파일에서 손으로 옮겨 적은 것이다 — 판정 규칙을 시험 안에서 불러 기대값을 만들지 않는다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class DigestModelDirectoryTests {

	private static final Instant NOW = Instant.parse("2026-11-18T03:00:00Z");

	@Autowired
	DigestModelDirectory directory;

	@Autowired
	AccountProvisioning provisioning;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from llm_credentials where user_id in (select id from users where email like 'digest-model-%')")
			.update();
		this.jdbc.sql("delete from users where email like 'digest-model-%'").update();
	}

	@Test
	void theChosenModelWhenItsKeyIsValid() {
		UserId user = account("digest-model-chosen@example.com");
		key(user, "anthropic", "VALID");
		key(user, "openai", "VALID");
		choose(user, "anthropic", "claude-opus-5-5");

		assertThat(this.directory.modelOf(user)).map(LlmModel::id).contains("claude-opus-5-5");
	}

	@Test
	void theFirstModelOfAProviderWithAValidKeyWhenTheChosenKeyIsInvalid() {
		UserId user = account("digest-model-fallback@example.com");
		key(user, "anthropic", "INVALID");
		key(user, "openai", "VALID");
		choose(user, "anthropic", "claude-opus-5-5");

		assertThat(this.directory.modelOf(user)).map(LlmModel::id).contains("gpt-6.1-terra");
	}

	@Test
	void theFirstModelWhenNothingIsChosenOrTheSettingsRowIsMissing() {
		UserId chose = account("digest-model-none-chosen@example.com");
		key(chose, "anthropic", "VALID");
		UserId unset = account("digest-model-unset@example.com");
		key(unset, "anthropic", "VALID");
		this.jdbc.sql("delete from user_settings where user_id = :id").param("id", unset.value()).update();

		assertThat(this.directory.modelOf(chose)).map(LlmModel::id).contains("claude-sonnet-5-5");
		assertThat(this.directory.modelOf(unset)).map(LlmModel::id).contains("claude-sonnet-5-5");
	}

	@Test
	void emptyWithoutAValidKey() {
		UserId invalid = account("digest-model-invalid@example.com");
		key(invalid, "openai", "INVALID");
		UserId keyless = account("digest-model-keyless@example.com");

		assertThat(this.directory.modelOf(invalid)).isEmpty();
		assertThat(this.directory.modelOf(keyless)).isEmpty();
	}

	@Test
	void emptyForAnAccountThatIsNotActiveOrDoesNotExist() {
		UserId suspended = account("digest-model-suspended@example.com");
		key(suspended, "openai", "VALID");
		UserId withdrawn = account("digest-model-withdrawn@example.com");
		key(withdrawn, "openai", "VALID");
		UserId active = account("digest-model-active@example.com");
		key(active, "openai", "VALID");
		this.jdbc.sql("update users set status = 'SUSPENDED' where id = :id").param("id", suspended.value()).update();
		this.jdbc.sql("update users set status = 'DELETED' where id = :id").param("id", withdrawn.value()).update();

		assertThat(this.directory.modelOf(suspended)).isEmpty();
		assertThat(this.directory.modelOf(withdrawn)).isEmpty();
		assertThat(this.directory.modelOf(new UserId(Long.MAX_VALUE))).isEmpty();
		// 대조 — 같은 키의 쓰는 계정은 모델이 있다
		assertThat(this.directory.modelOf(active)).map(LlmModel::id).contains("gpt-6.1-terra");
	}

	private UserId account(String email) {
		ProvisionResult result = this.transaction.execute(status -> this.provisioning.provision(Onboarding.OPEN_SIGN_UP,
				EmailAddress.parse(email).orElseThrow(), new DisplayName("사용자"),
				PasswordPolicy.normalize("correct horse battery"), NOW));
		assertThat(result).isInstanceOf(ProvisionResult.Created.class);
		return ((ProvisionResult.Created) result).user();
	}

	private void key(UserId user, String provider, String status) {
		this.jdbc.sql("""
				insert into llm_credentials (user_id, provider, ciphertext, encrypted_dek, kek_version, key_hint, status,
				  validated_at, created_at)
				values (:user, :provider, '\\x01'::bytea, '\\x01'::bytea, 1, '', :status, now(), now())
				""").param("user", user.value()).param("provider", provider).param("status", status).update();
	}

	private void choose(UserId user, String provider, String model) {
		this.jdbc.sql("update user_settings set llm_provider = :provider, llm_model = :model where user_id = :user")
			.param("provider", provider)
			.param("model", model)
			.param("user", user.value())
			.update();
	}

}
