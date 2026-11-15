package net.yozmnews.credential.application;

import java.time.Instant;
import java.time.InstantSource;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.credential.CredentialInvalidated;
import net.yozmnews.credential.domain.crypto.EnvelopeCipher;
import net.yozmnews.credential.domain.key.KeyStatus;
import net.yozmnews.credential.domain.key.LlmKeyRepository;
import net.yozmnews.credential.domain.key.StoredLlmKey;
import net.yozmnews.llm.KeyValidationResult;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.StoredKey;
import net.yozmnews.llm.StoredKeySource;
import net.yozmnews.platform.UserId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code llm} 의 공급 인터페이스 {@link StoredKeySource} 의 구현 — 저장한 키를 열어 건네고, 물은 결과로 키의 상태를 전이한다.
 * docs/specs/credential.md §4 · credential-R13 ~ credential-R15
 * <p>
 * 결과는 물은 그 키 ID 의 행을 잠근 뒤에만 쓴다 — 그 사이 지워졌거나 교체된 키의 결과는 버린다. 검증 시각은 성공일 때만 고친다. 무효 시각은
 * 마이크로초로 자른다 — 저장소와 이벤트가 같은 값을 본다.
 */
@Service
class StoredKeys implements StoredKeySource {

	private final LlmKeyRepository keys;

	private final EnvelopeCipher cipher;

	private final ApplicationEventPublisher events;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	StoredKeys(LlmKeyRepository keys, EnvelopeCipher cipher, ApplicationEventPublisher events,
			TransactionTemplate transaction) {
		this(keys, cipher, events, transaction, InstantSource.system());
	}

	StoredKeys(LlmKeyRepository keys, EnvelopeCipher cipher, ApplicationEventPublisher events,
			TransactionTemplate transaction, InstantSource clock) {
		this.keys = Objects.requireNonNull(keys, "keys");
		this.cipher = Objects.requireNonNull(cipher, "cipher");
		this.events = Objects.requireNonNull(events, "events");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public Optional<StoredKey> find(UserId user, Provider provider) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(provider, "provider");
		return this.keys.findByUserIdAndProvider(user.value(), provider.key())
			.map(key -> new StoredKey(key.id(), this.cipher.open(key.sealed(), key.owner())));
	}

	@Override
	public void checked(long keyId, KeyValidationResult result) {
		Optional<KeyStatus> next = KeyStatus.after(result);
		if (next.isEmpty()) {
			return;
		}
		Instant now = this.clock.instant().truncatedTo(ChronoUnit.MICROS);
		this.transaction.executeWithoutResult(status -> this.keys.lockById(keyId)
			.ifPresent(key -> record(key, next.get(), now)));
	}

	private void record(StoredLlmKey key, KeyStatus next, Instant now) {
		boolean invalidated = key.invalidatedBy(next);
		this.keys.save(key.checked(next, (next == KeyStatus.VALID) ? now : key.validatedAt()));
		if (invalidated) {
			this.events.publishEvent(new CredentialInvalidated(key.owner().user(), key.provider(), key.id(), now));
		}
	}

}
