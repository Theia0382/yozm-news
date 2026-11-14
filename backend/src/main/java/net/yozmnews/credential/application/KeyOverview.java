package net.yozmnews.credential.application;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.yozmnews.credential.domain.key.LlmKeyRepository;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;
import org.springframework.stereotype.Service;

/**
 * 세션 사용자의 키 목록 — 공급자 선언 순으로 공급자마다 하나. docs/specs/credential.md §4 · credential-R17
 */
@Service
public class KeyOverview {

	private final LlmKeyRepository keys;

	KeyOverview(LlmKeyRepository keys) {
		this.keys = Objects.requireNonNull(keys, "keys");
	}

	public List<ProviderKey> of(UserId user) {
		Objects.requireNonNull(user, "user");
		Map<Provider, KeyView> stored = this.keys.findByUserId(user.value())
			.stream()
			.map(KeyView::of)
			.collect(Collectors.toMap(KeyView::provider, Function.identity()));
		return Arrays.stream(Provider.values())
			.map(provider -> new ProviderKey(provider, stored.get(provider)))
			.toList();
	}

}
