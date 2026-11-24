package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.AccountCreated;
import net.yozmnews.user.AccountEvents;
import net.yozmnews.user.UserTables;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 계정을 만드는 정본 — 한 트랜잭션에서 계정 · 자격(· 복구 코드)을 함께 만들고, 길마다 역할 · 강제 변경 · 복구 코드가 갈리며, 같은 이메일이면
 * 아무것도 쓰지 않는다. 계정을 만든 트랜잭션에서 계정 생김을 하나 내고, 그 트랜잭션은 관심 설정을 쓰지 않는다. 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: user-R13 · user-R15 · user-R16 · user-R28 · user-R30 · account-R35
 */
@Import({ TestcontainersConfiguration.class, AccountProvisioningTests.CommitProbe.class })
@RecordApplicationEvents
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

	@Autowired
	SettingsBeforeCommit settingsBeforeCommit;

	@BeforeEach
	@AfterEach
	void reset() {
		AccountEvents.awaitCreatedHandled(this.jdbc);
		UserTables.deleteAll(this.jdbc);
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

	@ParameterizedTest
	@EnumSource(Onboarding.class)
	void everyWayPublishesOneAccountCreatedAndWritesNoSettingsRowInItsTransaction(Onboarding onboarding,
			ApplicationEvents events) {
		UserId user = created(provision(onboarding, onboarding.name().toLowerCase() + "@example.com", "사용자")).user();

		assertThat(events.stream(AccountCreated.class)).containsExactly(new AccountCreated(user, NOW));
		assertThat(this.settingsBeforeCommit.rows.get(user)).as("계정을 만든 트랜잭션이 커밋하기 직전의 설정 행 수").isZero();
	}

	@Test
	void theSameEmailPublishesNothing(ApplicationEvents events) {
		created(provision(Onboarding.OPEN_SIGN_UP, "judy@example.com", "주디"));
		long afterFirst = events.stream(AccountCreated.class).count();

		assertThat(provision(Onboarding.OPERATOR, "judy@example.com", "주디")).isInstanceOf(ProvisionResult.EmailTaken.class);
		assertThat(events.stream(AccountCreated.class)).as("같은 이메일의 두 번째 만들기가 더한 계정 생김")
			.hasSize((int) afterFirst);
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

	/** 계정 생김을 낸 트랜잭션이 커밋하기 직전, 그 트랜잭션 안에서 본 그 사용자의 설정 행 수 */
	static final class SettingsBeforeCommit {

		final Map<UserId, Long> rows = new ConcurrentHashMap<>();

		private final JdbcClient jdbc;

		SettingsBeforeCommit(JdbcClient jdbc) {
			this.jdbc = jdbc;
		}

		@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
		public void on(AccountCreated created) {
			this.rows.put(created.user(), this.jdbc.sql("select count(*) from user_settings where user_id = :user")
				.param("user", created.user().value())
				.query(Long.class)
				.single());
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class CommitProbe {

		@Bean
		SettingsBeforeCommit settingsBeforeCommit(JdbcClient jdbc) {
			return new SettingsBeforeCommit(jdbc);
		}

	}

	private int recoveryRows(UserId user) {
		return this.jdbc.sql("select count(*) from user_recovery_codes where user_id = :id")
			.param("id", user.value())
			.query(Integer.class)
			.single();
	}

}
