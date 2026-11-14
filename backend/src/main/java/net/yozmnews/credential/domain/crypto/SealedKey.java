package net.yozmnews.credential.domain.crypto;

import java.util.Objects;

/**
 * 봉인한 키 — 키의 암호문, KEK 로 감싼 DEK, 그 KEK 의 버전. 저장 꼴은 credential-R3 이다.
 * <p>
 * 문자열 표현은 길이와 버전만 낸다.
 *
 * @param ciphertext 키의 암호문
 * @param encryptedDek KEK 로 감싼 DEK
 * @param kekVersion DEK 를 감싼 KEK 의 버전
 */
public record SealedKey(byte[] ciphertext, byte[] encryptedDek, int kekVersion) {

	public SealedKey {
		Objects.requireNonNull(ciphertext, "ciphertext");
		Objects.requireNonNull(encryptedDek, "encryptedDek");
	}

	@Override
	public String toString() {
		return "SealedKey[ciphertext=" + this.ciphertext.length + "바이트, encryptedDek=" + this.encryptedDek.length
				+ "바이트, kekVersion=" + this.kekVersion + "]";
	}

}
