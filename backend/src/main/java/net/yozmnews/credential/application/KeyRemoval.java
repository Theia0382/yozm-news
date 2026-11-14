package net.yozmnews.credential.application;

import java.util.Objects;

import net.yozmnews.credential.domain.key.LlmKeyRepository;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 키 삭제 유스케이스 — 그 주인의 행을 곧바로 지운다. 없으면 아무 일도 없다. docs/specs/credential.md §4 · credential-R16
 */
@Service
public class KeyRemoval {

	private final LlmKeyRepository keys;

	KeyRemoval(LlmKeyRepository keys) {
		this.keys = Objects.requireNonNull(keys, "keys");
	}

	@Transactional
	public void remove(UserId user, Provider provider) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(provider, "provider");
		this.keys.lockByOwner(user.value(), provider.key()).ifPresent(this.keys::delete);
	}

}
