package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.account.AccountStatus;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.credential.PasswordCredential;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import net.yozmnews.user.domain.throttle.EmailDigest;
import net.yozmnews.user.domain.throttle.FailureRecord;
import net.yozmnews.user.domain.throttle.LoginFailures;
import net.yozmnews.user.domain.throttle.ThrottledAttempt;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 로그인의 대조 경로 — 있는 계정 · 없는 계정 모두 Argon2 대조를 한 번 하고(없는 계정은 더미 해시), 128 코드 포인트를 넘는 비밀번호는 대조하지
 * 않는다. 시간이 아니라 같은 단계를 지나는지(대조 호출 횟수)로 본다.
 * <p>
 * 지키는 규칙: user-R17 · user-R19
 */
class SignInHashingPathTests {

	private static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");

	private final AccountRepository accounts = mock(AccountRepository.class);

	private final PasswordCredentialRepository credentials = mock(PasswordCredentialRepository.class);

	private final LoginFailures failures = mock(LoginFailures.class);

	private final PasswordHashing hashing = spy(new PasswordHashing());

	private final SignIn signIn = new SignIn(this.accounts, this.credentials, this.hashing,
			new FailureThrottle(this.failures));

	SignInHashingPathTests() {
		when(this.failures.lock(any(), any())).thenReturn(FailureRecord.NONE);
		Account account = Account.create(EmailAddress.parse("alice@example.com").orElseThrow(), new DisplayName("앨리스"),
				AccountRole.USER, NOW);
		ReflectionTestUtils.setField(account, "id", 7L);
		when(this.accounts.findByEmail("alice@example.com")).thenReturn(Optional.of(account));
		// 스텁 설정 안에서 spy 를 부르지 않게 해시를 먼저 만든다
		PasswordCredential credential = PasswordCredential.of(new UserId(7),
				this.hashing.hash("correct horse"), false, NOW);
		when(this.credentials.findById(7L)).thenReturn(Optional.of(credential));
		Account suspended = Account.create(EmailAddress.parse("carol@example.com").orElseThrow(), new DisplayName("캐럴"),
				AccountRole.USER, NOW);
		ReflectionTestUtils.setField(suspended, "id", 8L);
		ReflectionTestUtils.setField(suspended, "status", AccountStatus.SUSPENDED);
		when(this.accounts.findByEmail("carol@example.com")).thenReturn(Optional.of(suspended));
		PasswordCredential suspendedCredential = PasswordCredential.of(new UserId(8),
				this.hashing.hash("correct horse"), false, NOW);
		when(this.credentials.findById(8L)).thenReturn(Optional.of(suspendedCredential));
	}

	@Test
	void existingAccountIsComparedOnce() {
		assertThat(this.signIn.attempt("alice@example.com", "wrong horse", NOW))
			.isInstanceOf(SignInOutcome.Rejected.class);

		verify(this.hashing, times(1)).matches(anyString(), anyString());
		verify(this.hashing, never()).matchesNobody(anyString());
	}

	@Test
	void missingAccountIsComparedOnceAgainstTheDummyHash() {
		assertThat(this.signIn.attempt("nobody@example.com", "wrong horse", NOW))
			.isInstanceOf(SignInOutcome.Rejected.class);

		verify(this.hashing, never()).matches(anyString(), anyString());
		verify(this.hashing, times(1)).matchesNobody(anyString());
	}

	@Test
	void inactiveAccountIsComparedOnceAgainstTheDummyHashEvenWithTheRightPassword() {
		assertThat(this.signIn.attempt("carol@example.com", "correct horse", NOW))
			.isInstanceOf(SignInOutcome.Rejected.class);

		verify(this.hashing, never()).matches(anyString(), anyString());
		verify(this.hashing, times(1)).matchesNobody(anyString());
	}

	@Test
	void unreadableEmailIsComparedOnceAgainstTheDummyHash() {
		assertThat(this.signIn.attempt(" Not An Email ", "wrong horse", NOW)).isInstanceOf(SignInOutcome.Rejected.class);

		verify(this.hashing, times(1)).matchesNobody(anyString());
		// 이메일로 읽을 수 없어도 앞뒤 공백을 지운 소문자로 실패 기록을 센다
		verify(this.failures).lock(ThrottledAttempt.SIGN_IN, EmailDigest.of("not an email"));
	}

	@Test
	void overlongPasswordIsNotHashed() {
		assertThat(this.signIn.attempt("alice@example.com", "a".repeat(129), NOW))
			.isInstanceOf(SignInOutcome.Rejected.class);

		verify(this.hashing, never()).matches(anyString(), anyString());
		verify(this.hashing, never()).matchesNobody(anyString());
	}

	@Test
	void rightPasswordSignsIn() {
		assertThat(this.signIn.attempt(" Alice@Example.com ", "correct horse", NOW))
			.isEqualTo(new SignInOutcome.SignedIn(new UserId(7)));
		verify(this.failures).lock(ThrottledAttempt.SIGN_IN, EmailDigest.of("alice@example.com"));
		verify(this.failures).forget(ThrottledAttempt.SIGN_IN, EmailDigest.of("alice@example.com"));
	}

}
