package net.yozmnews.source.domain.profile;

import java.net.URI;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 프로필의 피드 하나.
 *
 * @param url 피드 URL(절대 http · https URL)
 * @param pollInterval 이 피드의 권장 수집 주기 — 없을 수 있다. 실제 주기는 {@link PollIntervalPolicy}
 */
public record FeedSpec(FeedKey key, URI url, String section, @Nullable PollInterval pollInterval) {

	public FeedSpec {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(url, "url");
		Objects.requireNonNull(section, "section");
	}

}
