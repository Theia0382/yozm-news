package net.yozmnews.credential.adapter.in.web;

import net.yozmnews.credential.application.ProviderKey;
import net.yozmnews.llm.Provider;
import org.jspecify.annotations.Nullable;

/**
 * 공급자 하나와 그 공급자의 키 — docs/specs/credential.md §3 · credential-R17
 *
 * @param provider 공급자
 * @param key 키 — 없으면 null
 */
record ProviderKeyResponse(Provider provider, @Nullable LlmKeyResponse key) {

	static ProviderKeyResponse of(ProviderKey entry) {
		return new ProviderKeyResponse(entry.provider(), (entry.key() == null) ? null : LlmKeyResponse.of(entry.key()));
	}

}
