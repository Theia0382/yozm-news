package net.yozmnews.credential.application;

import java.util.Objects;

import net.yozmnews.llm.Provider;
import org.jspecify.annotations.Nullable;

/**
 * 공급자 하나와 그 공급자의 키.
 *
 * @param provider 공급자
 * @param key 키 — 없으면 null
 */
public record ProviderKey(Provider provider, @Nullable KeyView key) {

	public ProviderKey {
		Objects.requireNonNull(provider, "provider");
	}

}
