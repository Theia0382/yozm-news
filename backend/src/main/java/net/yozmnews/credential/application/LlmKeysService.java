package net.yozmnews.credential.application;

import java.util.Objects;

import net.yozmnews.credential.LlmKeys;
import net.yozmnews.credential.domain.key.LlmKeyRepository;
import net.yozmnews.platform.UserId;
import org.springframework.stereotype.Service;

/**
 * 공개 API {@link LlmKeys} 의 구현 — docs/specs/credential.md §4 · credential-R18
 */
@Service
class LlmKeysService implements LlmKeys {

	private final LlmKeyRepository keys;

	LlmKeysService(LlmKeyRepository keys) {
		this.keys = Objects.requireNonNull(keys, "keys");
	}

	@Override
	public boolean present(UserId user) {
		Objects.requireNonNull(user, "user");
		return this.keys.existsByUserId(user.value());
	}

}
