package net.yozmnews.credential.domain.key;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.credential.domain.crypto.KeyOwner;
import net.yozmnews.credential.domain.crypto.SealedKey;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 키 aggregate — docs/specs/credential.md §4 · credential-R11 · credential-R14
 * <p>
 * 평문을 갖지 않는다. 공급자는 {@code Provider.key()} 로 저장한다. 문자열 표현은 ID · 공급자 · 상태뿐이다.
 */
@Table("llm_credentials")
public final class StoredLlmKey {

	@Id
	private @Nullable Long id;

	private final long userId;

	private final String provider;

	private final byte[] ciphertext;

	private final byte[] encryptedDek;

	private final int kekVersion;

	private final String keyHint;

	private final KeyStatus status;

	private final Instant validatedAt;

	private final Instant createdAt;

	@PersistenceCreator
	StoredLlmKey(@Nullable Long id, long userId, String provider, byte[] ciphertext, byte[] encryptedDek,
			int kekVersion, String keyHint, KeyStatus status, Instant validatedAt, Instant createdAt) {
		this.id = id;
		this.userId = userId;
		this.provider = Objects.requireNonNull(provider, "provider");
		this.ciphertext = Objects.requireNonNull(ciphertext, "ciphertext");
		this.encryptedDek = Objects.requireNonNull(encryptedDek, "encryptedDek");
		this.kekVersion = kekVersion;
		this.keyHint = Objects.requireNonNull(keyHint, "keyHint");
		this.status = Objects.requireNonNull(status, "status");
		this.validatedAt = Objects.requireNonNull(validatedAt, "validatedAt");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	/** 검증을 통과한 새 키 — 상태 {@code VALID}, 검증 시각과 만든 시각은 {@code now} */
	public static StoredLlmKey register(KeyOwner owner, SealedKey sealed, String hint, Instant now) {
		return new StoredLlmKey(null, owner.user().value(), owner.provider().key(), sealed.ciphertext(),
				sealed.encryptedDek(), sealed.kekVersion(), hint, KeyStatus.VALID, now, now);
	}

	/**
	 * 저장한 키로 물은 결과를 반영한다.
	 * @param validatedAt 검증 시각 — 바꾸지 않으려면 지금 값({@link #validatedAt()})을 넘긴다
	 */
	public StoredLlmKey checked(KeyStatus status, Instant validatedAt) {
		return new StoredLlmKey(this.id, this.userId, this.provider, this.ciphertext, this.encryptedDek,
				this.kekVersion, this.keyHint, status, validatedAt, this.createdAt);
	}

	/** DEK 를 다시 감싼 키 — 키의 암호문은 그대로여야 한다 */
	public StoredLlmKey rewrapped(SealedKey sealed) {
		return new StoredLlmKey(this.id, this.userId, this.provider, sealed.ciphertext(), sealed.encryptedDek(),
				sealed.kekVersion(), this.keyHint, this.status, this.validatedAt, this.createdAt);
	}

	/** 다음 상태가 이 키를 무효로 만드는가 — 지금 {@code VALID} 이고 다음이 {@code INVALID} */
	public boolean invalidatedBy(KeyStatus next) {
		return this.status == KeyStatus.VALID && next == KeyStatus.INVALID;
	}

	/**
	 * 키 ID 를 돌려준다.
	 * @throws IllegalStateException 아직 저장하지 않았을 때
	 */
	public long id() {
		if (this.id == null) {
			throw new IllegalStateException("저장하지 않은 키다");
		}
		return this.id;
	}

	public KeyOwner owner() {
		return new KeyOwner(new UserId(this.userId), provider());
	}

	public Provider provider() {
		return Provider.ofKey(this.provider)
			.orElseThrow(() -> new IllegalStateException("모르는 공급자 키가 저장돼 있다: " + this.provider));
	}

	public SealedKey sealed() {
		return new SealedKey(this.ciphertext, this.encryptedDek, this.kekVersion);
	}

	public int kekVersion() {
		return this.kekVersion;
	}

	public String hint() {
		return this.keyHint;
	}

	public KeyStatus status() {
		return this.status;
	}

	public Instant validatedAt() {
		return this.validatedAt;
	}

	public Instant createdAt() {
		return this.createdAt;
	}

	@Override
	public String toString() {
		return "StoredLlmKey[id=" + this.id + ", provider=" + this.provider + ", status=" + this.status + "]";
	}

}
