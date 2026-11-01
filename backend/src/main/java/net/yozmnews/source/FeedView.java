package net.yozmnews.source;

import java.net.URI;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 소스의 피드 하나의 운영 설정 · 프로필 정보 · 수집 상태 — docs/specs/source.md §3.
 * <p>
 * 프로필에 그 피드가 없으면({@code inProfile} 거짓) {@code url} · {@code section} · {@code effectivePollIntervalMinutes} 는
 * null 이다. {@code health} 는 수집 상태다.
 */
public record FeedView(FeedId id, String feedKey, boolean enabled, @Nullable Integer pollIntervalMinutes,
		boolean inProfile, @Nullable URI url, @Nullable String section,
		@Nullable Integer effectivePollIntervalMinutes, FeedHealthView health) {

	public FeedView {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(feedKey, "feedKey");
		Objects.requireNonNull(health, "health");
	}

}
