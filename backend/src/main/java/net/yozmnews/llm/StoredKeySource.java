package net.yozmnews.llm;

import java.util.Optional;

import net.yozmnews.platform.UserId;
import org.springframework.modulith.NamedInterface;

/**
 * 저장한 키의 공급 인터페이스 — docs/specs/llm.md §3 · llm-R17 · llm-R18
 */
@NamedInterface("stored-key")
public interface StoredKeySource {

	/** 그 사용자 · 공급자의 저장한 키를 평문으로 낸다 */
	Optional<StoredKey> find(UserId user, Provider provider);

	/** 그 키로 공급자에 물은 결과를 알린다 */
	void checked(long keyId, KeyValidationResult result);

}
