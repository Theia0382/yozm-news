package net.yozmnews.llm;

import java.util.Optional;

import net.yozmnews.platform.UserId;

/**
 * 저장한 키가 없는 공급 인터페이스 — 관문의 결선만 보는 시험이 관문이 요구하는 빈으로 둔다. 저장한 키의 길은 부르지 않는다.
 */
public final class NoStoredKeys implements StoredKeySource {

	@Override
	public Optional<StoredKey> find(UserId user, Provider provider) {
		return Optional.empty();
	}

	@Override
	public void checked(long keyId, KeyValidationResult result) {
	}

}
