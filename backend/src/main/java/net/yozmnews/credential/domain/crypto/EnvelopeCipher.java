package net.yozmnews.credential.domain.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import net.yozmnews.platform.SecretValue;

/**
 * 봉투 암호 — 정본 · docs/specs/credential.md §4 · credential-R1 ~ credential-R4
 * <p>
 * 키는 키마다 새로 만든 DEK 로, DEK 는 KEK 로 AES-256-GCM 암호화한다. 두 암호문 모두 판 바이트 · 난수 · GCM 출력을 이은 꼴이고, 추가 인증
 * 데이터가 주인(사용자 · 공급자)을 묶는다. 실패 문구는 고정 문구와 예외 클래스 이름뿐이다.
 */
public final class EnvelopeCipher {

	/** 저장 꼴의 판 바이트 */
	static final byte FORMAT = 0x01;

	/** GCM 난수의 길이(바이트) */
	static final int NONCE_BYTES = 12;

	/** GCM 태그의 길이(비트) */
	static final int TAG_BITS = 128;

	/** DEK 의 길이(바이트) — AES-256 */
	static final int DEK_BYTES = 32;

	/** 열기 실패 문구의 머리 — 뒤에 까닭(고정 문구나 예외 클래스 이름)을 잇는다 */
	static final String OPEN_FAILED = "저장한 키를 열지 못했다 — ";

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private final KeyEncryptionKeys keks;

	private final SecureRandom random;

	public EnvelopeCipher(KeyEncryptionKeys keks) {
		this(keks, new SecureRandom());
	}

	EnvelopeCipher(KeyEncryptionKeys keks, SecureRandom random) {
		this.keks = Objects.requireNonNull(keks, "keks");
		this.random = Objects.requireNonNull(random, "random");
	}

	/** 새 DEK 로 키를 암호화하고 그 DEK 를 활성 버전의 KEK 로 감싼다 */
	public SealedKey seal(SecretValue key, KeyOwner owner) {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(owner, "owner");
		byte[] dek = new byte[DEK_BYTES];
		this.random.nextBytes(dek);
		try {
			int version = this.keks.activeVersion();
			byte[] ciphertext = encrypt(new SecretKeySpec(dek, "AES"), keyBinding(owner),
					key.reveal().getBytes(StandardCharsets.UTF_8));
			byte[] encryptedDek = encrypt(this.keks.key(version), dekBinding(owner, version), dek);
			return new SealedKey(ciphertext, encryptedDek, version);
		}
		finally {
			Arrays.fill(dek, (byte) 0);
		}
	}

	/**
	 * 봉인한 키를 연다.
	 * @throws IllegalStateException 주인이 다르거나, 변조 · 손상됐거나, 모르는 판이거나, 설정에 없는 KEK 버전일 때 — 문구에 키 · 암호문 ·
	 * KEK 를 담지 않는다
	 */
	public SecretValue open(SealedKey sealed, KeyOwner owner) {
		Objects.requireNonNull(sealed, "sealed");
		Objects.requireNonNull(owner, "owner");
		byte[] dek = unwrap(sealed, owner);
		try {
			byte[] plain = decrypt(new SecretKeySpec(dek, "AES"), keyBinding(owner), sealed.ciphertext());
			return SecretValue.of(new String(plain, StandardCharsets.UTF_8));
		}
		finally {
			Arrays.fill(dek, (byte) 0);
		}
	}

	/**
	 * DEK 를 활성 버전의 KEK 로 다시 감싼다 — 키의 암호문은 그대로다.
	 * @throws IllegalStateException 감싼 DEK 를 열지 못했을 때({@link #open} 과 같다)
	 */
	public SealedKey rewrap(SealedKey sealed, KeyOwner owner) {
		Objects.requireNonNull(sealed, "sealed");
		Objects.requireNonNull(owner, "owner");
		byte[] dek = unwrap(sealed, owner);
		try {
			int version = this.keks.activeVersion();
			return new SealedKey(sealed.ciphertext(), encrypt(this.keks.key(version), dekBinding(owner, version), dek),
					version);
		}
		finally {
			Arrays.fill(dek, (byte) 0);
		}
	}

	/** 새 DEK 를 감싸는 KEK 버전 */
	public int activeVersion() {
		return this.keks.activeVersion();
	}

	private byte[] unwrap(SealedKey sealed, KeyOwner owner) {
		if (!this.keks.has(sealed.kekVersion())) {
			throw new IllegalStateException(OPEN_FAILED + "설정에 없는 KEK 버전 " + sealed.kekVersion());
		}
		return decrypt(this.keks.key(sealed.kekVersion()), dekBinding(owner, sealed.kekVersion()),
				sealed.encryptedDek());
	}

	private byte[] encrypt(SecretKey key, byte[] binding, byte[] plain) {
		byte[] nonce = new byte[NONCE_BYTES];
		this.random.nextBytes(nonce);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
			cipher.updateAAD(binding);
			byte[] output = cipher.doFinal(plain);
			byte[] stored = new byte[1 + NONCE_BYTES + output.length];
			stored[0] = FORMAT;
			System.arraycopy(nonce, 0, stored, 1, NONCE_BYTES);
			System.arraycopy(output, 0, stored, 1 + NONCE_BYTES, output.length);
			return stored;
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("키를 암호화하지 못했다 — " + ex.getClass().getSimpleName());
		}
	}

	private static byte[] decrypt(SecretKey key, byte[] binding, byte[] stored) {
		if (stored.length < 1 + NONCE_BYTES + TAG_BITS / 8) {
			throw new IllegalStateException(OPEN_FAILED + "암호문이 짧다");
		}
		if (stored[0] != FORMAT) {
			throw new IllegalStateException(OPEN_FAILED + "모르는 판");
		}
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 1, NONCE_BYTES));
			cipher.updateAAD(binding);
			return cipher.doFinal(stored, 1 + NONCE_BYTES, stored.length - 1 - NONCE_BYTES);
		}
		catch (GeneralSecurityException ex) {
			// 예외를 잇지 않는다 — 문구와 스택에 암호 라이브러리의 값이 실리지 않게 클래스 이름만 남긴다
			throw new IllegalStateException(OPEN_FAILED + ex.getClass().getSimpleName());
		}
	}

	/** 키의 추가 인증 데이터 — 주인을 묶는다(credential-R2) */
	private static byte[] keyBinding(KeyOwner owner) {
		return ("yozmnews:llm-key:v1:" + owner.user().value() + ":" + owner.provider().key())
			.getBytes(StandardCharsets.UTF_8);
	}

	/** DEK 의 추가 인증 데이터 — 주인과 KEK 버전을 묶는다(credential-R2) */
	private static byte[] dekBinding(KeyOwner owner, int kekVersion) {
		return ("yozmnews:llm-dek:v1:" + owner.user().value() + ":" + owner.provider().key() + ":" + kekVersion)
			.getBytes(StandardCharsets.UTF_8);
	}

}
