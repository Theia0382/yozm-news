package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.bootstrap.FirstAdminDecision;
import net.yozmnews.user.domain.bootstrap.FirstAdminLock;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 첫 운영자 기동 처리 — 실제 PostgreSQL 에서 값으로 만든 운영자 계정, 다시 기동할 때의 경고, 값이 없을 때 · 하나만 · 이미 있는 이메일 · 정지한
 * 운영자만 남은 경우, 함께 기동한 두 프로세스를 본다. 로그 · 기동 실패 문구에 비밀번호가 없다.
 * <p>
 * 지키는 규칙: user-R28 · user-R29 · user-R34
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class FirstAdminBootstrapTests {

	private static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");

	private static final String EMAIL = "Admin.Ops@Example.org";

	private static final String PASSWORD = "initial horse battery";

	@Autowired
	AccountProvisioning provisioning;

	@Autowired
	AccountRepository accounts;

	@Autowired
	FirstAdminLock lock;

	@Autowired
	PasswordHashing hashing;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from users").update();
	}

	@Test
	void valuesMakeAForcedChangeAdminNamedAfterTheEmail(CapturedOutput output) {
		assertThat(bootstrap(EMAIL, PASSWORD).run()).isInstanceOf(FirstAdminDecision.Create.class);

		Map<String, Object> admin = this.jdbc.sql("""
				select u.email, u.display_name, u.role, u.status, c.must_change, c.password_hash
				from users u join user_password_credentials c on c.user_id = u.id
				""").query().singleRow();
		assertThat(admin).containsEntry("email", "admin.ops@example.org")
			.containsEntry("display_name", "admin.ops")
			.containsEntry("role", "ADMIN")
			.containsEntry("status", "ACTIVE")
			.containsEntry("must_change", true);
		assertThat(this.hashing.matches(PASSWORD, (String) admin.get("password_hash"))).isTrue();
		assertThat(this.jdbc.sql("select count(*) from user_recovery_codes").query(Integer.class).single())
			.as("복구 코드는 비밀번호를 바꾼 직후에 준다")
			.isZero();
		assertThat(output).contains("첫 운영자 계정을 만들었다 — admin.ops@example.org").doesNotContain(PASSWORD);
	}

	@Test
	void startingAgainOnlyWarnsThatTheValuesAreLeft(CapturedOutput output) {
		bootstrap(EMAIL, PASSWORD).run();

		assertThat(bootstrap(EMAIL, PASSWORD).run()).isEqualTo(new FirstAdminDecision.WarnLeftover());
		// 운영자가 있으면 하나만 남았든 규칙을 어기든 경고만 한다
		assertThat(bootstrap(EMAIL, null).run()).isEqualTo(new FirstAdminDecision.WarnLeftover());
		assertThat(bootstrap("not an email", "short").run()).isEqualTo(new FirstAdminDecision.WarnLeftover());
		assertThat(bootstrap(null, null).run()).isEqualTo(new FirstAdminDecision.Nothing());
		assertThat(admins()).isOne();
		assertThat(output).contains("첫 운영자 값이 남아 있다").doesNotContain(PASSWORD);
	}

	@Test
	void noValuesAndNoAdminWarnsAndStartsWithoutAnAdmin(CapturedOutput output) {
		assertThat(bootstrap(null, "").run()).isEqualTo(new FirstAdminDecision.WarnNoAdmin());

		assertThat(this.jdbc.sql("select count(*) from users").query(Integer.class).single()).isZero();
		assertThat(output).contains("운영자 계정이 없다 — 첫 운영자의 yozmnews.first-admin.email(YOZMNEWS_FIRST_ADMIN_EMAIL)");
	}

	@Test
	void oneValueWithoutAnAdminStopsTheStartNamingTheVariable() {
		assertThatIllegalStateException().isThrownBy(() -> bootstrap(EMAIL, null).run())
			.withMessage("첫 운영자를 만들지 못해 기동하지 않는다 — yozmnews.first-admin.password(YOZMNEWS_FIRST_ADMIN_PASSWORD) 이 "
					+ "없다 — 첫 운영자의 이메일 · 초기 비밀번호는 함께 준다");
		assertThatIllegalStateException().isThrownBy(() -> bootstrap(EMAIL, "short").run())
			.withMessageContaining("비밀번호 규칙")
			.withMessageNotContaining("short");
		assertThat(admins()).isZero();
	}

	@Test
	void anEmailAlreadyTakenStopsTheStartAndTouchesNothing() {
		this.accounts.save(Account.create(EmailAddress.parse("admin.ops@example.org").orElseThrow(),
				new DisplayName("먼저 가입한 사람"), AccountRole.USER, NOW));

		assertThatIllegalStateException().isThrownBy(() -> bootstrap(EMAIL, PASSWORD).run())
			.withMessageContaining("이미 있는 계정의 이메일");
		assertThat(this.jdbc.sql("select role from users").query(String.class).single()).isEqualTo("USER");
	}

	@Test
	void onlyASuspendedAdminLeftCountsAsNoAdmin() {
		long suspended = this.accounts.save(Account.create(EmailAddress.parse("old@example.org").orElseThrow(),
				new DisplayName("예전 운영자"), AccountRole.ADMIN, NOW)).id().value();
		this.jdbc.sql("update users set status = 'SUSPENDED' where id = :id").param("id", suspended).update();

		assertThat(bootstrap(EMAIL, PASSWORD).run()).isInstanceOf(FirstAdminDecision.Create.class);
		assertThat(admins()).isOne();
	}

	@Test
	void twoProcessesStartingTogetherMakeOneAdmin() throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		List<FirstAdminDecision> decisions = new ArrayList<>();
		try {
			List<Future<FirstAdminDecision>> futures = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				Callable<FirstAdminDecision> run = () -> {
					start.await();
					return bootstrap(EMAIL, PASSWORD).run();
				};
				futures.add(pool.submit(run));
			}
			start.countDown();
			for (Future<FirstAdminDecision> future : futures) {
				decisions.add(future.get());
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(decisions).filteredOn(FirstAdminDecision.Create.class::isInstance).hasSize(1);
		assertThat(decisions).contains(new FirstAdminDecision.WarnLeftover());
		assertThat(admins()).isOne();
	}

	private FirstAdminBootstrap bootstrap(@Nullable String email, @Nullable String password) {
		return new FirstAdminBootstrap(new FirstAdminProperties(email, password), this.provisioning, this.accounts,
				this.lock, () -> NOW);
	}

	private int admins() {
		return this.jdbc.sql("select count(*) from users where role = 'ADMIN' and status = 'ACTIVE'")
			.query(Integer.class)
			.single();
	}

}
