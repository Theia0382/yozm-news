package net.yozmnews.source.domain.profile;

import java.util.Objects;

/**
 * 수집 프로필 안의 피드 식별자 — source-R7 · source-R11
 */
public record FeedKey(String value) {

	public FeedKey {
		Identifiers.requireSlug(Objects.requireNonNull(value, "value"), "피드 식별자");
	}

	@Override
	public String toString() {
		return this.value;
	}

}
