package net.yozmnews.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.account.AccountStatus;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.credential.PasswordCredential;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 계정 · 자격 aggregate 의 저장 — 실제 PostgreSQL 에서 계정을 이메일로 찾고, 같은 이메일은 고유 제약이 막으며, 자격은 계정마다 하나이고 계정과 함께
 * 지워지며, 강제 변경 표시가 저장 · 읽기를 오가고 비밀번호를 바꾸면 풀린다.
 * <p>
 * 지키는 규칙: user-R13 · user-R16 · user-R29
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class AccountRepositoryTests {

	private static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	JdbcClient jdbc;

	@Test
	void savesAndFindsAnAccountByEmail() {
		Account saved = this.accounts
			.save(Account.create(EmailAddress.parse("alice@example.com").orElseThrow(),
					new DisplayName("앨리스"), AccountRole.ADMIN, NOW));

		Account read = this.accounts.findByEmail("alice@example.com").orElseThrow();

		assertThat(read.id()).isEqualTo(saved.id());
		assertThat(read.email().value()).isEqualTo("alice@example.com");
		assertThat(read.displayName()).isEqualTo("앨리스");
		assertThat(read.role()).isEqualTo(AccountRole.ADMIN);
		assertThat(read.status()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(read.active()).isTrue();
		assertThat(read.createdAt()).isEqualTo(NOW);
		assertThat(this.accounts.findByEmail("bob@example.com")).isEmpty();
	}

	@Test
	void sameEmailIsRejectedByTheUniqueConstraint() {
		this.accounts.save(Account.create(EmailAddress.parse("alice@example.com").orElseThrow(), new DisplayName("앨리스"),
				AccountRole.USER, NOW));

		assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> this.accounts.save(
				Account.create(EmailAddress.parse(" ALICE@example.com").orElseThrow(),
						new DisplayName("다른 앨리스"), AccountRole.USER, NOW)));
	}

	@Test
	void readsInactiveStatusesStoredAsText() {
		Account saved = this.accounts
			.save(Account.create(EmailAddress.parse("carol@example.com").orElseThrow(),
					new DisplayName("캐럴"), AccountRole.USER, NOW));
		this.jdbc.sql("update users set status = 'SUSPENDED' where id = :id")
			.param("id", saved.id().value())
			.update();

		Account read = this.accounts.findById(saved.id().value()).orElseThrow();

		assertThat(read.status()).isEqualTo(AccountStatus.SUSPENDED);
		assertThat(read.active()).isFalse();
	}

	@Test
	void credentialRoundTripsAndGoesWithItsAccount() {
		UserId id = this.accounts
			.save(Account.create(EmailAddress.parse("dave@example.com").orElseThrow(),
					new DisplayName("데이브"), AccountRole.USER, NOW))
			.id();
		this.credentials.save(PasswordCredential.of(id, "$argon2id$hash", false, NOW));

		PasswordCredential read = this.credentials.findById(id.value()).orElseThrow();
		assertThat(read.userId()).isEqualTo(id);
		assertThat(read.passwordHash()).isEqualTo("$argon2id$hash");
		assertThat(read.passwordChangedAt()).isEqualTo(NOW);
		assertThat(read.toString()).doesNotContain("$argon2id$hash");

		this.jdbc.sql("delete from users where id = :id").param("id", id.value()).update();
		assertThat(this.credentials.findById(id.value())).isEmpty();
	}

	@Test
	void forcedChangeRoundTripsAndChangingThePasswordLiftsIt() {
		UserId id = this.accounts
			.save(Account.create(EmailAddress.parse("grace@example.com").orElseThrow(),
					new DisplayName("그레이스"), AccountRole.USER, NOW))
			.id();
		this.credentials.save(PasswordCredential.of(id, "$argon2id$initial", true, NOW));

		PasswordCredential initial = this.credentials.findById(id.value()).orElseThrow();
		assertThat(initial.mustChange()).isTrue();

		Instant later = Instant.parse("2026-11-13T10:00:00Z");
		this.credentials.save(initial.changed("$argon2id$mine", later));

		assertThat(this.jdbc.sql("select must_change, password_hash from user_password_credentials where user_id = :id")
			.param("id", id.value())
			.query()
			.singleRow()).containsEntry("must_change", false).containsEntry("password_hash", "$argon2id$mine");
		assertThat(this.credentials.findById(id.value()).orElseThrow().passwordChangedAt()).isEqualTo(later);
	}

	@Test
	void secondCredentialForTheSameAccountIsRejected() {
		UserId id = this.accounts
			.save(Account.create(EmailAddress.parse("erin@example.com").orElseThrow(),
					new DisplayName("에린"), AccountRole.USER, NOW))
			.id();
		this.credentials.save(PasswordCredential.of(id, "$argon2id$hash", false, NOW));

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> this.credentials.save(PasswordCredential.of(id, "$argon2id$other", false, NOW)));
	}

	@Test
	void credentialNeedsAnExistingAccount() {
		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> this.credentials.save(PasswordCredential.of(new UserId(999_999),
					"$argon2id$hash", false, NOW)));
	}

}
