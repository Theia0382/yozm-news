package net.yozmnews.user.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.user.AccountAdministration;
import net.yozmnews.user.AccountCreationResult;
import net.yozmnews.user.InitialPasswordResetResult;
import net.yozmnews.user.PasswordProblem;
import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.account.Onboarding;
import net.yozmnews.user.domain.credential.PasswordCredential;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import net.yozmnews.user.domain.credential.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 운영자의 계정 운영 — {@link AccountAdministration} 의 구현. docs/specs/user.md §4.
 * <p>
 * 계정 생성은 계정을 만드는 정본의 운영자 길(일반 사용자 · 강제 변경 · 복구 코드는 비밀번호를 바꾼 직후)이다. 초기 비밀번호 다시 정하기는 자격
 * 행을 잠근 뒤 강제 변경 대기인지 판정하고(같은 계정의 비밀번호 변경과 줄을 선다), 마지막으로 그 계정의 세션을 모두 지운다 — 앞 초기 비밀번호로
 * 열어 둔 세션이 남으면 그 세션으로 비밀번호를 먼저 바꿀 수 있다.
 */
@Service
class OperatorAccountService implements AccountAdministration {

	private static final Logger logger = LoggerFactory.getLogger(OperatorAccountService.class);

	private final AccountProvisioning provisioning;

	private final AccountRepository accounts;

	private final PasswordCredentialRepository credentials;

	private final PasswordHashing hashing;

	private final OtherSessions sessions;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	OperatorAccountService(AccountProvisioning provisioning, AccountRepository accounts,
			PasswordCredentialRepository credentials, PasswordHashing hashing, OtherSessions sessions,
			TransactionTemplate transaction) {
		this(provisioning, accounts, credentials, hashing, sessions, transaction, InstantSource.system());
	}

	OperatorAccountService(AccountProvisioning provisioning, AccountRepository accounts,
			PasswordCredentialRepository credentials, PasswordHashing hashing, OtherSessions sessions,
			TransactionTemplate transaction, InstantSource clock) {
		this.provisioning = Objects.requireNonNull(provisioning, "provisioning");
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.credentials = Objects.requireNonNull(credentials, "credentials");
		this.hashing = Objects.requireNonNull(hashing, "hashing");
		this.sessions = Objects.requireNonNull(sessions, "sessions");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public AccountCreationResult create(String email, String displayName, String initialPassword) {
		Optional<EmailAddress> address = EmailAddress.parse(email);
		if (address.isEmpty()) {
			return new AccountCreationResult.EmailMalformed();
		}
		Optional<DisplayName> name = DisplayName.parse(displayName);
		if (name.isEmpty()) {
			return new AccountCreationResult.DisplayNameMalformed();
		}
		String password = PasswordPolicy.normalize(initialPassword);
		Optional<PasswordProblem> problem = PasswordPolicy.check(password);
		if (problem.isPresent()) {
			return new AccountCreationResult.PasswordRejected(problem.get());
		}
		Instant now = this.clock.instant();
		AccountCreationResult result = this.provisioning.inOwnTransaction(
				() -> switch (this.provisioning.provision(Onboarding.OPERATOR, address.get(), name.get(), password,
						now)) {
					case ProvisionResult.Created created -> new AccountCreationResult.Created(created.user());
					case ProvisionResult.EmailTaken taken -> new AccountCreationResult.EmailTaken();
				}, AccountCreationResult.EmailTaken::new);
		if (result instanceof AccountCreationResult.Created created) {
			logger.info("운영자가 계정을 만들었다 — 사용자 {}", created.user().value());
		}
		return result;
	}

	@Override
	public InitialPasswordResetResult resetInitialPassword(String email, String initialPassword) {
		String password = PasswordPolicy.normalize(initialPassword);
		Optional<PasswordProblem> problem = PasswordPolicy.check(password);
		if (problem.isPresent()) {
			return new InitialPasswordResetResult.PasswordRejected(problem.get());
		}
		Instant now = this.clock.instant();
		InitialPasswordResetResult result = this.transaction.execute(status -> {
			Optional<Account> account = EmailAddress.parse(email)
				.flatMap(address -> this.accounts.findByEmail(address.value()))
				.filter(Account::active);
			if (account.isEmpty()) {
				return new InitialPasswordResetResult.NoAccount();
			}
			UserId user = account.get().id();
			this.credentials.lockForUpdate(user.value());
			PasswordCredential credential = this.credentials.findById(user.value())
				.orElseThrow(() -> new IllegalStateException("계정의 자격이 없다: " + user.value()));
			if (!credential.mustChange()) {
				return new InitialPasswordResetResult.NotPending();
			}
			this.credentials.save(credential.resetInitial(this.hashing.hash(password), now));
			this.sessions.endAll(user);
			return new InitialPasswordResetResult.Reset();
		});
		if (result instanceof InitialPasswordResetResult.Reset) {
			logger.info("운영자가 첫 로그인 전 계정의 초기 비밀번호를 다시 정했다");
		}
		return Objects.requireNonNull(result);
	}

}
