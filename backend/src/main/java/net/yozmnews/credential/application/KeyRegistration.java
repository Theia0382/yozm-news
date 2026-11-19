package net.yozmnews.credential.application;

import java.time.Instant;
import java.time.InstantSource;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import net.yozmnews.credential.domain.crypto.EnvelopeCipher;
import net.yozmnews.credential.domain.crypto.KeyOwner;
import net.yozmnews.credential.domain.key.KeyHint;
import net.yozmnews.credential.domain.key.KeyOwnerLock;
import net.yozmnews.credential.domain.key.LlmKeyRepository;
import net.yozmnews.credential.domain.key.StoredLlmKey;
import net.yozmnews.llm.KeyValidationResult;
import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.SecretValue;
import net.yozmnews.platform.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 키 등록 · 교체 유스케이스 — docs/specs/credential.md §4 · credential-R10 · credential-R11
 * <p>
 * 검증은 트랜잭션 밖에서 하고, 성공하면 트랜잭션 하나에서 주인을 잠근 뒤 옛 행을 지우고 새 행을 넣는다. 같은 주인의 등록이 겹치면 주인
 * 잠금({@link KeyOwnerLock})에서 줄을 서 차례로 교체한다 — 고유 제약에 지는 등록이 없다.
 */
@Service
public class KeyRegistration {

	private final LlmClientFactory llm;

	private final LlmKeyRepository keys;

	private final KeyOwnerLock ownerLock;

	private final EnvelopeCipher cipher;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	KeyRegistration(LlmClientFactory llm, LlmKeyRepository keys, KeyOwnerLock ownerLock, EnvelopeCipher cipher,
			TransactionTemplate transaction, InstantSource clock) {
		this.llm = Objects.requireNonNull(llm, "llm");
		this.keys = Objects.requireNonNull(keys, "keys");
		this.ownerLock = Objects.requireNonNull(ownerLock, "ownerLock");
		this.cipher = Objects.requireNonNull(cipher, "cipher");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** 키를 검증하고, 성공하면 그 주인의 키로 저장한다 — 있던 키는 새 키로 바뀐다 */
	public RegistrationOutcome register(UserId user, Provider provider, SecretValue key) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(key, "key");
		return switch (this.llm.validate(provider, key)) {
			case KeyValidationResult.Rejected rejected -> new RegistrationOutcome.Rejected(rejected.kind());
			case KeyValidationResult.Valid valid -> new RegistrationOutcome.Registered(store(new KeyOwner(user, provider), key));
		};
	}

	private KeyView store(KeyOwner owner, SecretValue key) {
		Instant now = this.clock.instant().truncatedTo(ChronoUnit.MICROS);
		return Objects.requireNonNull(this.transaction.execute(status -> {
			this.ownerLock.lock(owner);
			this.keys.lockByOwner(owner.user().value(), owner.provider().key()).ifPresent(this.keys::delete);
			return KeyView
				.of(this.keys.save(StoredLlmKey.register(owner, this.cipher.seal(key, owner), KeyHint.of(key), now)));
		}));
	}

}
