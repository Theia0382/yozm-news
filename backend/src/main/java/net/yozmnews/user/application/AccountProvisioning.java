package net.yozmnews.user.application;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.account.Onboarding;
import net.yozmnews.user.domain.credential.PasswordCredential;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import net.yozmnews.user.domain.credential.PasswordPolicy;
import net.yozmnews.user.domain.recovery.RecoveryCodeRepository;
import net.yozmnews.user.domain.secret.RecoveryCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 계정을 만드는 정본 — 모든 길(공개 가입 · 초대 가입 · 운영자 계정 생성 · 첫 운영자)이 이것을 거친다. docs/specs/user.md §4.
 * <p>
 * 부르는 쪽의 트랜잭션 안에서 계정 · 자격(· 복구 코드)을 함께 만든다 — "계정마다 자격 하나"는 DB 가 강제하지 않으므로 같은 트랜잭션이 지킨다.
 * 길마다의 차이(역할 · 강제 변경 · 만들 때 복구 코드)는 {@link Onboarding} 이 정한다. 비밀번호 해시는 이메일 중복과 무관하게 한 번 계산해
 * 이미 있는 이메일과 새 이메일의 계산량을 맞춘다.
 */
@Service
class AccountProvisioning {

	private final AccountRepository accounts;

	private final PasswordCredentialRepository credentials;

	private final RecoveryCodeRepository recoveryCodes;

	private final PasswordHashing hashing;

	private final TransactionTemplate transaction;

	private final SecureRandom random = new SecureRandom();

	AccountProvisioning(AccountRepository accounts, PasswordCredentialRepository credentials,
			RecoveryCodeRepository recoveryCodes, PasswordHashing hashing, TransactionTemplate transaction) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.credentials = Objects.requireNonNull(credentials, "credentials");
		this.recoveryCodes = Objects.requireNonNull(recoveryCodes, "recoveryCodes");
		this.hashing = Objects.requireNonNull(hashing, "hashing");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
	}

	/**
	 * 계정을 만드는 유스케이스의 트랜잭션 경계 — 일을 트랜잭션 하나에서 하고, 먼저 본 뒤 끼어든 같은 이메일이 고유 제약으로 트랜잭션을 깨면
	 * 트랜잭션 바깥에서 같은 이메일의 결과로 옮긴다. 가입 · 운영자 계정 생성 · 첫 운영자가 함께 쓴다.
	 * @param emailTaken 같은 이메일일 때의 결과
	 */
	<T> T inOwnTransaction(Supplier<T> work, Supplier<T> emailTaken) {
		try {
			return Objects.requireNonNull(this.transaction.execute(status -> work.get()));
		}
		catch (DuplicateKeyException ex) {
			return emailTaken.get();
		}
	}

	/**
	 * 계정을 만든다 — 부르는 쪽의 트랜잭션 안에서만. 같은 이메일의 계정이 있으면 아무것도 쓰지 않는다. 먼저 본 뒤 끼어든 같은 이메일은
	 * {@code users.email} 의 고유 제약이 막는다({@code DuplicateKeyException} — 부르는 쪽 트랜잭션 바깥이 결과로 옮긴다).
	 * @param normalizedPassword {@link PasswordPolicy#normalize} 로 정규화했고 규칙을 지키는 비밀번호
	 * @throws IllegalArgumentException 비밀번호가 규칙을 어길 때 — 부르는 쪽이 먼저 거른다
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	ProvisionResult provision(Onboarding onboarding, EmailAddress email, DisplayName name, String normalizedPassword,
			Instant now) {
		if (PasswordPolicy.check(normalizedPassword).isPresent()) {
			throw new IllegalArgumentException("비밀번호 규칙을 어긴 비밀번호로 계정을 만들지 않는다");
		}
		String hash = this.hashing.hash(normalizedPassword);
		if (this.accounts.findByEmail(email.value()).isPresent()) {
			return new ProvisionResult.EmailTaken();
		}
		UserId user = this.accounts.save(Account.create(email, name, onboarding.role(), now)).id();
		this.credentials.save(PasswordCredential.of(user, hash, onboarding.mustChangePassword(), now));
		Optional<RecoveryCode> recoveryCode = Optional.empty();
		if (onboarding.recoveryCodeAtCreation()) {
			RecoveryCode code = RecoveryCode.generate(this.random);
			this.recoveryCodes.replace(user, code.digest(), now);
			recoveryCode = Optional.of(code);
		}
		return new ProvisionResult.Created(user, recoveryCode);
	}

	@Override
	public String toString() {
		return "AccountProvisioning";
	}

}
