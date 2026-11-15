package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.AccountAdministration;
import net.yozmnews.user.AccountCreationResult;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.credential.PasswordCredential;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import net.yozmnews.user.domain.recovery.RecoveryCodeRepository;
import net.yozmnews.user.domain.secret.RecoveryCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 세션 지우기가 실패하면 자격 변경이 남지 않는다 — 세션 저장소는 자기 트랜잭션으로 곧바로 커밋하므로, 유스케이스는 세션 지우기를 커밋 직전의 마지막
 * 단계로 불러 그 실패가 자격 변경 · 복구 코드 · 알림을 롤백시키게 한다.
 * <p>
 * 지키는 규칙: user-R29 · user-R30 · user-R35
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class SessionEndingFailureTests {

	private static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");

	private static final String PASSWORD = "correct horse battery";

	private static final String NEW_PASSWORD = "brand new horse staple";

	private static final String CODE = "0123-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXYZ";

	@MockitoBean
	OtherSessions sessions;

	@Autowired
	PasswordChange passwordChange;

	@Autowired
	AccountRecovery recovery;

	@Autowired
	AccountAdministration administration;

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	RecoveryCodeRepository recoveryCodes;

	@Autowired
	PasswordHashing hashing;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from users").update();
		this.jdbc.sql("delete from login_failures").update();
	}

	@Test
	void failedSessionEndingLeavesThePasswordAsItWas() {
		UserId user = account("alice@example.com");
		doThrow(new IllegalStateException("세션 저장소 실패")).when(this.sessions).endAllExcept(any(), anyString());

		assertThatIllegalStateException()
			.isThrownBy(() -> this.passwordChange.change(user, PASSWORD, NEW_PASSWORD, "this-session", NOW));

		String hash = this.credentials.findById(user.value()).orElseThrow().passwordHash();
		assertThat(this.hashing.matches(PASSWORD, hash)).isTrue();
		assertThat(notices(user)).isZero();
	}

	@Test
	void failedSessionEndingLeavesTheRecoveryCodeAndPasswordAsTheyWere() {
		UserId user = account("bob@example.com");
		this.recoveryCodes.replace(user, RecoveryCode.parse(CODE).orElseThrow().digest(), NOW);
		doThrow(new IllegalStateException("세션 저장소 실패")).when(this.sessions).endAll(any());

		assertThatIllegalStateException()
			.isThrownBy(() -> this.recovery.reset("bob@example.com", CODE, NEW_PASSWORD, NOW));

		assertThat(this.recoveryCodes.find(user).orElseThrow().matches(RecoveryCode.parse(CODE).orElseThrow().digest()))
			.isTrue();
		String hash = this.credentials.findById(user.value()).orElseThrow().passwordHash();
		assertThat(this.hashing.matches(PASSWORD, hash)).isTrue();
		assertThat(notices(user)).isZero();
	}

	@Test
	void failedSessionEndingLeavesTheInitialPasswordAsItWas() {
		AccountCreationResult created = this.administration.create("carol@example.com", "캐럴", PASSWORD);
		UserId user = ((AccountCreationResult.Created) created).user();
		doThrow(new IllegalStateException("세션 저장소 실패")).when(this.sessions).endAll(any());

		assertThatIllegalStateException()
			.isThrownBy(() -> this.administration.resetInitialPassword("carol@example.com", NEW_PASSWORD));

		String hash = this.credentials.findById(user.value()).orElseThrow().passwordHash();
		assertThat(this.hashing.matches(PASSWORD, hash)).isTrue();
	}

	private UserId account(String email) {
		UserId id = this.accounts.save(Account.create(EmailAddress.parse(email).orElseThrow(), new DisplayName("시험"),
				AccountRole.USER, NOW)).id();
		this.credentials.save(PasswordCredential.of(id, this.hashing.hash(PASSWORD), false, NOW));
		return id;
	}

	private int notices(UserId user) {
		return this.jdbc.sql("select count(*) from user_notices where user_id = :id")
			.param("id", user.value())
			.query(Integer.class)
			.single();
	}

}
