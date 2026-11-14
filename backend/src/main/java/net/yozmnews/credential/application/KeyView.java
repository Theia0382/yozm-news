package net.yozmnews.credential.application;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.credential.domain.key.KeyStatus;
import net.yozmnews.credential.domain.key.StoredLlmKey;
import net.yozmnews.llm.Provider;

/**
 * 화면에 보이는 키 — 평문 · 암호문을 담지 않는다(credential-R19).
 *
 * @param provider 공급자
 * @param hint 힌트 — 없으면 빈 문자열
 * @param status 상태
 * @param validatedAt 마지막으로 성공한 검증의 시각
 * @param createdAt 만든 시각
 */
public record KeyView(Provider provider, String hint, KeyStatus status, Instant validatedAt, Instant createdAt) {

	public KeyView {
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(hint, "hint");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(validatedAt, "validatedAt");
		Objects.requireNonNull(createdAt, "createdAt");
	}

	static KeyView of(StoredLlmKey key) {
		return new KeyView(key.provider(), key.hint(), key.status(), key.validatedAt(), key.createdAt());
	}

}
