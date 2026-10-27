package net.yozmnews.source.domain.profile;

import java.util.Objects;

/**
 * 피드 식별자 — 프로필 안에서 고유하다. {@code feeds.feed_key} 에 저장되므로 한 번 쓰인 값은 바꾸지 않는다.
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
