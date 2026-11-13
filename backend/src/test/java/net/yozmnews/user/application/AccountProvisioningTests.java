package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.account.Onboarding;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import net.yozmnews.user.domain.credential.PasswordPolicy;
import net.yozmnews.user.domain.recovery.RecoveryCodeRepository;
import net.yozmnews.user.domain.secret.RecoveryCode;
import net.yozmnews.user.domain.secret.SecretDigest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 계정을 만드는 정본 — 한 트랜잭션에서 계정 · 자격(· 복구 코드)을 함께 만들고, 길마다 역할 · 강제 변경 · 복구 코드가 갈리며, 같은 이메일이면
 * 아무것도 쓰지 않는다. 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: user-R13 · user-R15 · user-R16 · user-R28 · user-R30
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class AccountProvisioningTests {

	private static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");

	private static final String PASSWORD = "correct horse battery";

	@Autowired
	AccountProvisioning provisioning;

	@Autowired
	PasswordHashing hashing;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	RecoveryCodeRepository recoveryCodes;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from users").update();
	}

	@Test
	void openSignUpMakesAUserWithARecoveryCodeAndNoForcedChange() {
		ProvisionResult.Created created = created(provision(Onboarding.OPEN_SIGN_UP, "Alice@Example.com", "앨리스"));

		Map<String, Object> row = row(created.user());
		assertThat(row).containsEntry("email", "alice@example.com")
			.containsEntry("display_name", "앨리스")
			.containsEntry("role", "USER")
			.containsEntry("status", "ACTIVE")
			.containsEntry("must_change", false);
		assertThat(this.hashing.matches(PASSWORD, (String) row.get("password_hash"))).isTrue();
		SecretDigest stored = SecretDigest.stored(recoveryDigest(created.user()));
		assertThat(created.recoveryCode()).isPresent();
		assertThat(stored.matches(created.recoveryCode().orElseThrow().digest()))
			.as("저장된 것은 돌려준 코드의 다이제스트다")
			.isTrue();
	}

	@Test
	void invitationAlsoMakesAUserWithARecoveryCode() {
		ProvisionResult.Created created = created(provision(Onboarding.INVITATION, "bob@example.com", "밥"));

		assertThat(row(created.user())).containsEntry("role", "USER").containsEntry("must_change", false);
		assertThat(created.recoveryCode()).isPresent();
	}

	@Test
	void operatorCreatedAccountMustChangeAndGetsNoRecoveryCodeYet() {
		ProvisionResult.Created created = created(provision(Onboarding.OPERATOR, "carol@example.com", "캐럴"));

		assertThat(row(created.user())).containsEntry("role", "USER").containsEntry("must_change", true);
		assertThat(this.credentials.findById(created.user().value()).orElseThrow().mustChange())
			.as("자격 aggregate 로 읽어도 강제 변경이다")
			.isTrue();
		assertThat(created.recoveryCode()).isEmpty();
		assertThat(recoveryRows(created.user())).isZero();
	}

	@Test
	void anotherRecoveryCodeReplacesTheAccountsOnlyOne() {
		ProvisionResult.Created created = created(provision(Onboarding.OPEN_SIGN_UP, "grace@example.com", "그레이스"));
		RecoveryCode first = created.recoveryCode().orElseThrow();
		RecoveryCode second = RecoveryCode.generate(new SecureRandom());

		this.recoveryCodes.replace(created.user(), second.digest(), NOW.plusSeconds(60));

		assertThat(recoveryRows(created.user())).isOne();
		SecretDigest stored = this.recoveryCodes.find(created.user()).orElseThrow();
		assertThat(stored.matches(second.digest())).isTrue();
		assertThat(stored.matches(first.digest())).as("앞 코드는 무효가 된다").isFalse();
	}

	@Test
	void firstAdminIsAnAdminThatMustChange() {
		ProvisionResult.Created created = created(provision(Onboarding.FIRST_ADMIN, "ops@example.com", "ops"));

		assertThat(row(created.user())).containsEntry("role", "ADMIN").containsEntry("must_change", true);
		assertThat(created.recoveryCode()).isEmpty();
	}

	@Test
	void sameEmailWritesNothing() {
		created(provision(Onboarding.OPEN_SIGN_UP, "dave@example.com", "데이브"));

		ProvisionResult again = provision(Onboarding.OPERATOR, " DAVE@example.com ", "다른 데이브");

		assertThat(again).isInstanceOf(ProvisionResult.EmailTaken.class);
		assertThat(this.jdbc.sql("select count(*) from users").query(Integer.class).single()).isOne();
		assertThat(this.jdbc.sql("select count(*) from user_password_credentials").query(Integer.class).single())
			.isOne();
		assertThat(this.jdbc.sql("select count(*) from user_recovery_codes").query(Integer.class).single()).isOne();
	}

	@Test
	void runsOnlyInsideTheCallersTransaction() {
		assertThatExceptionOfType(IllegalTransactionStateException.class)
			.isThrownBy(() -> this.provisioning.provision(Onboarding.OPEN_SIGN_UP, email("erin@example.com"),
					new DisplayName("에린"), PasswordPolicy.normalize(PASSWORD), NOW));
	}

	@Test
	void refusesAPasswordThatBreaksTheRules() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.transaction
			.execute(status -> this.provisioning.provision(Onboarding.OPEN_SIGN_UP, email("frank@example.com"),
					new DisplayName("프랭크"), "short", NOW)));
		assertThat(this.jdbc.sql("select count(*) from users").query(Integer.class).single()).isZero();
	}

	private ProvisionResult provision(Onboarding onboarding, String email, String name) {
		return this.transaction.execute(status -> this.provisioning.provision(onboarding, email(email),
				new DisplayName(name), PasswordPolicy.normalize(PASSWORD), NOW));
	}

	private static ProvisionResult.Created created(ProvisionResult result) {
		assertThat(result).isInstanceOf(ProvisionResult.Created.class);
		return (ProvisionResult.Created) result;
	}

	private static EmailAddress email(String raw) {
		return EmailAddress.parse(raw).orElseThrow();
	}

	private Map<String, Object> row(UserId user) {
		return this.jdbc.sql("""
				select u.email, u.display_name, u.role, u.status, c.must_change, c.password_hash
				from users u join user_password_credentials c on c.user_id = u.id where u.id = :id
				""").param("id", user.value()).query().singleRow();
	}

	private byte[] recoveryDigest(UserId user) {
		return this.jdbc.sql("select code_digest from user_recovery_codes where user_id = :id")
			.param("id", user.value())
			.query(byte[].class)
			.single();
	}

	private int recoveryRows(UserId user) {
		return this.jdbc.sql("select count(*) from user_recovery_codes where user_id = :id")
			.param("id", user.value())
			.query(Integer.class)
			.single();
	}

}
