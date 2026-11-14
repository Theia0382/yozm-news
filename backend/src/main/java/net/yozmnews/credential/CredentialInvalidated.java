package net.yozmnews.credential;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;

/**
 * 키가 무효가 됐다 — docs/specs/credential.md §3 · credential-R15
 */
public record CredentialInvalidated(UserId user, Provider provider, long keyId, Instant invalidatedAt) {

	public CredentialInvalidated {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(invalidatedAt, "invalidatedAt");
	}

}
