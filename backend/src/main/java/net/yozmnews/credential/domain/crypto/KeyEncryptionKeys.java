package net.yozmnews.credential.domain.crypto;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * KEK 의 집합 — 버전마다 키 하나이고 활성 버전은 가장 큰 번호다. docs/specs/credential.md §4 · credential-R5 · credential-R8
 * <p>
 * 문자열 표현은 버전 목록만 낸다.
 */
public final class KeyEncryptionKeys {

	/** KEK 의 길이(바이트) — AES-256 */
	public static final int KEY_BYTES = 32;

	private final TreeMap<Integer, SecretKey> keys;

	private KeyEncryptionKeys(TreeMap<Integer, SecretKey> keys) {
		this.keys = keys;
	}

	/**
	 * @throws IllegalArgumentException 비었거나, 버전이 1 미만이거나, 키의 길이가 {@value #KEY_BYTES} 바이트가 아닐 때 — 문구에 값을
	 * 담지 않는다
	 */
	public static KeyEncryptionKeys of(Map<Integer, byte[]> keys) {
		Objects.requireNonNull(keys, "keys");
		if (keys.isEmpty()) {
			throw new IllegalArgumentException("KEK 가 하나도 없다");
		}
		TreeMap<Integer, SecretKey> copy = new TreeMap<>();
		keys.forEach((version, key) -> {
			if (version < 1) {
				throw new IllegalArgumentException("KEK 버전은 1 이상이어야 한다: " + version);
			}
			if (key.length != KEY_BYTES) {
				throw new IllegalArgumentException("KEK 는 " + KEY_BYTES + "바이트여야 한다 — 버전 " + version);
			}
			copy.put(version, new SecretKeySpec(key, "AES"));
		});
		return new KeyEncryptionKeys(copy);
	}

	/** 새 DEK 를 감싸는 버전 — 가장 큰 번호 */
	public int activeVersion() {
		return this.keys.lastKey();
	}

	public boolean has(int version) {
		return this.keys.containsKey(version);
	}

	/** 버전들 — 작은 번호부터 */
	public List<Integer> versions() {
		return List.copyOf(this.keys.keySet());
	}

	/**
	 * @throws IllegalStateException 그 버전이 없을 때
	 */
	SecretKey key(int version) {
		SecretKey key = this.keys.get(version);
		if (key == null) {
			throw new IllegalStateException("설정에 없는 KEK 버전이다: " + version);
		}
		return key;
	}

	@Override
	public String toString() {
		return "KeyEncryptionKeys[versions=" + this.keys.keySet() + "]";
	}

}
