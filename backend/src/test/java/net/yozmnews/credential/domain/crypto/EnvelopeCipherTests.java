package net.yozmnews.credential.domain.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import net.yozmnews.llm.Provider;
import net.yozmnews.platform.SecretValue;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.Test;

/**
 * 봉투 암호 — 봉인한 키는 같은 주인으로만 열리고, 다른 사용자 · 다른 공급자 · 변조 · 모르는 판 · 없는 KEK 버전에서는 열리지 않는다. 저장 꼴의
 * 길이와 판 바이트는 손으로 센 값과 견주고, 추가 인증 데이터는 시험이 손으로 적은 문면으로 직접 열어 본다. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: credential-R1 · credential-R2 · credential-R3 · credential-R4
 */
class EnvelopeCipherTests {

	private static final String KEY = "sk-test-0123456789abcdWXYZ";

	private static final KeyOwner ALICE_OPENAI = new KeyOwner(new UserId(7), Provider.OPENAI);

	private final EnvelopeCipher cipher = new EnvelopeCipher(keks(1));

	@Test
	void opensWhatItSealedForTheSameOwner() {
		SealedKey sealed = this.cipher.seal(SecretValue.of(KEY), ALICE_OPENAI);

		assertThat(this.cipher.open(sealed, ALICE_OPENAI).reveal()).isEqualTo(KEY);
		assertThat(sealed.kekVersion()).isEqualTo(1);
	}

	@Test
	void storesTheFormatByteNonceAndGcmOutput() {
		SealedKey sealed = this.cipher.seal(SecretValue.of(KEY), ALICE_OPENAI);

		// 판 1 + 난수 12 + 평문 26 + 태그 16
		assertThat(sealed.ciphertext()).hasSize(55);
		// 판 1 + 난수 12 + DEK 32 + 태그 16
		assertThat(sealed.encryptedDek()).hasSize(61);
		assertThat(sealed.ciphertext()[0]).isEqualTo((byte) 0x01);
		assertThat(sealed.encryptedDek()[0]).isEqualTo((byte) 0x01);
	}

	@Test
	void bindsTheOwnerIntoTheKeyAndTheOwnerAndKekVersionIntoTheDek() throws GeneralSecurityException {
		SealedKey sealed = this.cipher.seal(SecretValue.of(KEY), ALICE_OPENAI);
		byte[] kek = new byte[32];
		Arrays.fill(kek, (byte) 1);

		// 시험이 JCE 로 직접 연다 — 추가 인증 데이터는 credential-R2 의 문면을 손으로 적은 것이다(사용자 7 · openai · KEK 버전 1)
		byte[] dek = openStored(kek, "yozmnews:llm-dek:v1:7:openai:1", sealed.encryptedDek());
		byte[] plain = openStored(dek, "yozmnews:llm-key:v1:7:openai", sealed.ciphertext());

		assertThat(dek).hasSize(32);
		assertThat(new String(plain, StandardCharsets.UTF_8)).isEqualTo(KEY);
	}

	@Test
	void doesNotStoreThePlainKey() {
		SealedKey sealed = this.cipher.seal(SecretValue.of(KEY), ALICE_OPENAI);

		String plainHex = HexFormat.of().formatHex(KEY.getBytes(StandardCharsets.UTF_8));
		assertThat(HexFormat.of().formatHex(sealed.ciphertext())).doesNotContain(plainHex);
		assertThat(HexFormat.of().formatHex(sealed.encryptedDek())).doesNotContain(plainHex);
		assertThat(sealed.toString()).isEqualTo("SealedKey[ciphertext=55바이트, encryptedDek=61바이트, kekVersion=1]");
	}

	@Test
	void sealsTheSameKeyDifferentlyEachTime() {
		SealedKey first = this.cipher.seal(SecretValue.of(KEY), ALICE_OPENAI);
		SealedKey second = this.cipher.seal(SecretValue.of(KEY), ALICE_OPENAI);

		assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
		assertThat(first.encryptedDek()).isNotEqualTo(second.encryptedDek());
	}

	@Test
	void doesNotOpenForAnotherUser() {
		SealedKey sealed = this.cipher.seal(SecretValue.of(KEY), ALICE_OPENAI);

		assertThatIllegalStateException()
			.isThrownBy(() -> this.cipher.open(sealed, new KeyOwner(new UserId(8), Provider.OPENAI)))
			.withMessage("저장한 키를 열지 못했다 — AEADBadTagException")
			.withNoCause();
	}

	@Test
	void doesNotOpenForAnotherProvider() {
		SealedKey sealed = this.cipher.seal(SecretValue.of(KEY), ALICE_OPENAI);

		assertThatIllegalStateException()
			.isThrownBy(() -> this.cipher.open(sealed, new KeyOwner(new UserId(7), Provider.ANTHROPIC)))
			.withMessage("저장한 키를 열지 못했다 — AEADBadTagException");
	}

	@Test
	void doesNotOpenAKeyCiphertextMovedUnderAnotherOwnersDek() {
		KeyOwner bob = new KeyOwner(new UserId(8), Provider.OPENAI);
		SealedKey alice = this.cipher.seal(SecretValue.of(KEY), ALICE_OPENAI);
		SealedKey bobs = this.cipher.seal(SecretValue.of("sk-test-bob-0123456789abcd"), bob);

		// 사용자 ID 가 십진수라 7 과 70 · 1 과 11 처럼 앞이 같은 ID 도 다른 주인이다
		assertThatIllegalStateException().isThrownBy(
				() -> this.cipher.open(new SealedKey(alice.ciphertext(), bobs.encryptedDek(), 1), bob));
		assertThatIllegalStateException()
			.isThrownBy(() -> this.cipher.open(alice, new KeyOwner(new UserId(70), Provider.OPENAI)));
	}

	@Test
	void doesNotOpenTamperedBytes() {
		SealedKey sealed = this.cipher.seal(SecretValue.of(KEY), ALICE_OPENAI);

		assertThatIllegalStateException()
			.isThrownBy(() -> this.cipher
				.open(new SealedKey(flipLast(sealed.ciphertext()), sealed.encryptedDek(), 1), ALICE_OPENAI))
			.withMessage("저장한 키를 열지 못했다 — AEADBadTagException");
		assertThatIllegalStateException()
			.isThrownBy(() -> this.cipher
				.open(new SealedKey(sealed.ciphertext(), flipLast(sealed.encryptedDek()), 1), ALICE_OPENAI))
			.withMessage("저장한 키를 열지 못했다 — AEADBadTagException");
	}

	@Test
	void doesNotOpenAnUnknownFormatOrTruncatedBytes() {
		SealedKey sealed = this.cipher.seal(SecretValue.of(KEY), ALICE_OPENAI);
		byte[] otherFormat = sealed.ciphertext().clone();
		otherFormat[0] = 0x02;

		assertThatIllegalStateException()
			.isThrownBy(() -> this.cipher.open(new SealedKey(otherFormat, sealed.encryptedDek(), 1), ALICE_OPENAI))
			.withMessage("저장한 키를 열지 못했다 — 모르는 판");
		assertThatIllegalStateException()
			.isThrownBy(() -> this.cipher
				.open(new SealedKey(Arrays.copyOf(sealed.ciphertext(), 28), sealed.encryptedDek(), 1), ALICE_OPENAI))
			.withMessage("저장한 키를 열지 못했다 — 암호문이 짧다");
	}

	@Test
	void doesNotOpenWithAKekVersionMissingFromTheConfiguration() {
		SealedKey sealed = this.cipher.seal(SecretValue.of(KEY), ALICE_OPENAI);

		assertThatIllegalStateException()
			.isThrownBy(() -> new EnvelopeCipher(keks(2)).open(sealed, ALICE_OPENAI))
			.withMessage("저장한 키를 열지 못했다 — 설정에 없는 KEK 버전 1");
	}

	@Test
	void failureMessagesCarryNoKey() {
		SealedKey sealed = this.cipher.seal(SecretValue.of(KEY), ALICE_OPENAI);

		assertThatIllegalStateException()
			.isThrownBy(() -> this.cipher.open(sealed, new KeyOwner(new UserId(8), Provider.OPENAI)))
			.satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(KEY).doesNotContain("WXYZ"));
	}

	@Test
	void sealsWithTheLargestVersionAndRewrapsOnlyTheDek() {
		SealedKey old = this.cipher.seal(SecretValue.of(KEY), ALICE_OPENAI);
		EnvelopeCipher both = new EnvelopeCipher(keks(1, 2));

		SealedKey rewrapped = both.rewrap(old, ALICE_OPENAI);

		assertThat(both.activeVersion()).isEqualTo(2);
		assertThat(both.seal(SecretValue.of(KEY), ALICE_OPENAI).kekVersion()).isEqualTo(2);
		assertThat(rewrapped.kekVersion()).isEqualTo(2);
		assertThat(rewrapped.ciphertext()).isEqualTo(old.ciphertext());
		assertThat(rewrapped.encryptedDek()).hasSize(61).isNotEqualTo(old.encryptedDek());
		// 옛 버전을 지운 설정에서도 열린다 — DEK 가 새 버전으로 감싸였다
		assertThat(new EnvelopeCipher(keks(2)).open(rewrapped, ALICE_OPENAI).reveal()).isEqualTo(KEY);
	}

	/** 버전마다 그 번호로 채운 32바이트 KEK */
	static KeyEncryptionKeys keks(int... versions) {
		Map<Integer, byte[]> keys = new java.util.HashMap<>();
		for (int version : versions) {
			byte[] key = new byte[32];
			Arrays.fill(key, (byte) version);
			keys.put(version, key);
		}
		return KeyEncryptionKeys.of(keys);
	}

	/** 저장 꼴(판 1 · 난수 12 · GCM 출력)을 AES-256-GCM · 태그 128비트로 연다 */
	private static byte[] openStored(byte[] key, String binding, byte[] stored) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, stored, 1, 12));
		cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
		return cipher.doFinal(stored, 13, stored.length - 13);
	}

	private static byte[] flipLast(byte[] bytes) {
		byte[] copy = bytes.clone();
		copy[copy.length - 1] ^= 0x01;
		return copy;
	}

}
