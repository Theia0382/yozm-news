package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.user.InvitationState;
import net.yozmnews.user.IssuedInvitation;
import net.yozmnews.user.SignUpPolicyAdministration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 동시 가입 — 같은 초대로 둘이 동시에 가입하면 하나만 쓰고, 같은 이메일로 둘이 동시에 공개 가입하면 하나만 만든다(나머지는 가입할 수 없는
 * 이메일).
 * <p>
 * 지키는 규칙: user-R28 · user-R31 · user-R33
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class SignUpConcurrencyTests {

	private static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");

	@Autowired
	SignUp signUp;

	@Autowired
	InvitationService invitations;

	@Autowired
	SignUpPolicyAdministration policy;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from user_invitations").update();
		this.jdbc.sql("delete from users").update();
		this.policy.change(false, ChangeActor.OPERATOR);
	}

	@Test
	void oneInvitationSignsUpOnlyOnce() throws Exception {
		String token = ((IssuedInvitation.Issued) this.invitations.issue(null)).token();

		List<SignUpOutcome> outcomes = together(
				() -> this.signUp.acceptInvitation(token, new SignUpForm("one@example.com", "하나", "correct horse"), NOW),
				() -> this.signUp.acceptInvitation(token, new SignUpForm("two@example.com", "둘", "correct horse"), NOW));

		assertThat(outcomes).filteredOn(SignUpOutcome.SignedUp.class::isInstance).hasSize(1);
		assertThat(outcomes)
			.contains(new SignUpOutcome.InvitationUnusable(Optional.of(InvitationState.USED)));
		assertThat(this.jdbc.sql("select count(*) from users").query(Integer.class).single()).isOne();
	}

	@Test
	void oneEmailSignsUpOnlyOnce() throws Exception {
		this.policy.change(true, ChangeActor.OPERATOR);

		List<SignUpOutcome> outcomes = together(
				() -> this.signUp.openSignUp(new SignUpForm("same@example.com", "하나", "correct horse"), NOW),
				() -> this.signUp.openSignUp(new SignUpForm(" SAME@example.com", "둘", "correct horse"), NOW));

		assertThat(outcomes).filteredOn(SignUpOutcome.SignedUp.class::isInstance).hasSize(1);
		assertThat(outcomes).contains(new SignUpOutcome.EmailUnavailable());
		assertThat(this.jdbc.sql("select count(*) from users").query(Integer.class).single()).isOne();
		assertThat(this.jdbc.sql("select count(*) from user_password_credentials").query(Integer.class).single())
			.isOne();
	}

	private static List<SignUpOutcome> together(Callable<SignUpOutcome> first, Callable<SignUpOutcome> second)
			throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<SignUpOutcome>> futures = new ArrayList<>();
			for (Callable<SignUpOutcome> work : List.of(first, second)) {
				futures.add(pool.submit(() -> {
					start.await();
					return work.call();
				}));
			}
			start.countDown();
			List<SignUpOutcome> outcomes = new ArrayList<>();
			for (Future<SignUpOutcome> future : futures) {
				outcomes.add(future.get());
			}
			return outcomes;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
