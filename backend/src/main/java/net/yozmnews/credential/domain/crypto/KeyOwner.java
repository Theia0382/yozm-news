package net.yozmnews.credential.domain.crypto;

import java.util.Objects;

import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;

/**
 * 키의 주인 — 사용자와 공급자의 짝. 봉투 암호가 이 짝을 암호문에 묶는다(credential-R2).
 */
public record KeyOwner(UserId user, Provider provider) {

	public KeyOwner {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(provider, "provider");
	}

}
