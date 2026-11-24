package net.yozmnews.account.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.InitialPasswordResetResult;
import net.yozmnews.account.PasswordChangeMethod;
import net.yozmnews.account.PasswordChanged;
import net.yozmnews.account.domain.account.DisplayName;
import net.yozmnews.account.domain.account.EmailAddress;
import net.yozmnews.account.domain.account.Onboarding;
import net.yozmnews.account.domain.credential.PasswordPolicy;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.AccountEvents;
import net.yozmnews.user.UserTables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 비밀번호 바뀜 — 세션 사용자의 변경과 복구 코드 재설정은 자격을 바꾼 트랜잭션에서 {@link PasswordChanged} 하나를 내고, 첫 로그인의 강제 변경과
 * 운영자의 초기 비밀번호 다시 정하기는 내지 않는다. 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: account-R36
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@RecordApplicationEvents
class PasswordChangedEventTests {

	private static final Instant CREATED = Instant.parse("2026-11-20T01:00:00Z");

	private static final Instant CHANGED = Instant.parse("2026-11-20T02:00:00Z");

	private static final String PASSWORD = "correct horse battery";

	private static final String NEW_PASSWORD = "another horse battery";

	@Autowired
	AccountProvisioning provisioning;

	@Autowired
	PasswordChange passwordChange;

	@Autowired
	AccountRecovery recovery;

	@Autowired
	OperatorAccountService operatorAccounts;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		AccountEvents.awaitCreatedHandled(this.jdbc);
		AccountEvents.awaitPasswordChangeHandled(this.jdbc);
		UserTables.deleteAll(this.jdbc);
	}

	@Test
	void aSessionUsersChangePublishesOneChange(ApplicationEvents events) {
		UserId user = account(Onboarding.OPEN_SIGN_UP, "lena@example.com").user();

		assertThat(this.passwordChange.change(user, PASSWORD, NEW_PASSWORD, "kept-session", CHANGED))
			.isInstanceOf(PasswordChangeOutcome.Changed.class);

		assertThat(events.stream(PasswordChanged.class))
			.containsExactly(new PasswordChanged(user, PasswordChangeMethod.CHANGE, CHANGED));
	}

	@Test
	void aRecoveryResetPublishesOneRecovery(ApplicationEvents events) {
		ProvisionResult.Created created = account(Onboarding.OPEN_SIGN_UP, "mona@example.com");
		String code = created.recoveryCode().orElseThrow().display();

		assertThat(this.recovery.reset("mona@example.com", code, NEW_PASSWORD, CHANGED))
			.isInstanceOf(RecoveryOutcome.Reset.class);

		assertThat(events.stream(PasswordChanged.class))
			.containsExactly(new PasswordChanged(created.user(), PasswordChangeMethod.RECOVERY, CHANGED));
	}

	@Test
	void theForcedFirstChangePublishesNothing(ApplicationEvents events) {
		UserId user = account(Onboarding.OPERATOR, "nina@example.com").user();

		assertThat(this.passwordChange.change(user, PASSWORD, NEW_PASSWORD, "kept-session", CHANGED))
			.isInstanceOf(PasswordChangeOutcome.Changed.class);

		assertThat(events.stream(PasswordChanged.class)).isEmpty();
	}

	@Test
	void anOperatorResettingTheInitialPasswordPublishesNothing(ApplicationEvents events) {
		account(Onboarding.OPERATOR, "olga@example.com");

		assertThat(this.operatorAccounts.resetInitialPassword("olga@example.com", NEW_PASSWORD))
			.isInstanceOf(InitialPasswordResetResult.Reset.class);

		assertThat(events.stream(PasswordChanged.class)).isEmpty();
	}

	private ProvisionResult.Created account(Onboarding onboarding, String email) {
		ProvisionResult result = this.transaction.execute(status -> this.provisioning.provision(onboarding,
				EmailAddress.parse(email).orElseThrow(), new DisplayName("사용자"), PasswordPolicy.normalize(PASSWORD),
				CREATED));
		assertThat(result).isInstanceOf(ProvisionResult.Created.class);
		AccountEvents.awaitCreatedHandled(this.jdbc);
		return (ProvisionResult.Created) result;
	}

}
