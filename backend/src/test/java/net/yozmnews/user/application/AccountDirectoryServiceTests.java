package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.AccountDirectory;
import net.yozmnews.user.UserTables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 계정 공개 조회 — 있음은 상태와 무관하고, 쓰는 계정 · 쓰는 운영자 · 쪽은 계정 표만 읽어 ID 순으로 낸다. 쪽 크기는 1 ~ 500 이다. 실제
 * PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: account-R37
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class AccountDirectoryServiceTests {

	@Autowired
	AccountDirectory directory;

	@Autowired
	JdbcClient jdbc;

	private UserId admin;

	private UserId user;

	private UserId suspendedAdmin;

	private UserId withdrawn;

	private UserId secondAdmin;

	@BeforeEach
	void accounts() {
		UserTables.deleteAll(this.jdbc);
		this.admin = account("admin@example.com", "ADMIN", "ACTIVE");
		this.user = account("user@example.com", "USER", "ACTIVE");
		this.suspendedAdmin = account("suspended@example.com", "ADMIN", "SUSPENDED");
		this.withdrawn = account("withdrawn@example.com", "USER", "DELETED");
		this.secondAdmin = account("second@example.com", "ADMIN", "ACTIVE");
	}

	@AfterEach
	void clear() {
		UserTables.deleteAll(this.jdbc);
	}

	@Test
	void existsRegardlessOfStatusAndActiveOnlyForActiveAccounts() {
		assertThat(this.directory.exists(this.user)).isTrue();
		assertThat(this.directory.exists(this.withdrawn)).isTrue();
		assertThat(this.directory.exists(new UserId(this.secondAdmin.value() + 1000))).isFalse();

		assertThat(this.directory.active(this.user)).isTrue();
		assertThat(this.directory.active(this.suspendedAdmin)).isFalse();
		assertThat(this.directory.active(this.withdrawn)).isFalse();
		assertThat(this.directory.active(new UserId(this.secondAdmin.value() + 1000))).isFalse();
	}

	@Test
	void activeAdminsAreActiveAdminAccountsInIdOrder() {
		assertThat(this.directory.activeAdmins()).containsExactly(this.admin, this.secondAdmin);
	}

	@Test
	void pagesGoInIdOrderAfterTheGivenIdAndActivePagesSkipOtherStatuses() {
		long before = this.admin.value() - 1;

		assertThat(this.directory.page(before, 500)).containsExactly(this.admin, this.user, this.suspendedAdmin,
				this.withdrawn, this.secondAdmin);
		assertThat(this.directory.page(before, 2)).containsExactly(this.admin, this.user);
		assertThat(this.directory.page(this.user.value(), 2)).containsExactly(this.suspendedAdmin, this.withdrawn);

		assertThat(this.directory.activePage(before, 500)).containsExactly(this.admin, this.user, this.secondAdmin);
		assertThat(this.directory.activePage(this.admin.value(), 1)).containsExactly(this.user);
		assertThat(this.directory.activePage(this.secondAdmin.value(), 500)).isEmpty();
	}

	@Test
	void refusesAPageSizeOutsideOneToFiveHundred() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.page(0, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.page(0, 501));
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.activePage(0, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.activePage(0, 501));
		// 대조 — 경계 값은 받는다
		assertThat(this.directory.page(Long.MAX_VALUE - 1, 1)).isEmpty();
		assertThat(this.directory.activePage(Long.MAX_VALUE - 1, 500)).isEmpty();
	}

	private UserId account(String email, String role, String status) {
		return new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values (:email, :email, :role, :status, now(), 0) returning id
				""").param("email", email).param("role", role).param("status", status).query(Long.class).single());
	}

}
