package net.yozmnews.credential.application;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.credential.LlmKeys;
import net.yozmnews.credential.domain.key.KeyStatus;
import net.yozmnews.credential.domain.key.LlmKeyRepository;
import net.yozmnews.credential.domain.key.StoredLlmKey;
import net.yozmnews.llm.Provider;
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

	@Override
	public Set<Provider> providers(UserId user) {
		Objects.requireNonNull(user, "user");
		return providersOf(this.keys.findByUserId(user.value()));
	}

	@Override
	public Set<Provider> usable(UserId user) {
		Objects.requireNonNull(user, "user");
		return providersOf(this.keys.findByUserId(user.value())
			.stream()
			.filter(key -> key.status() == KeyStatus.VALID)
			.toList());
	}

	private static Set<Provider> providersOf(List<StoredLlmKey> keys) {
		Set<Provider> providers = EnumSet.noneOf(Provider.class);
		keys.forEach(key -> providers.add(key.provider()));
		return Collections.unmodifiableSet(providers);
	}

}
