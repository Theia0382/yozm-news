package net.yozmnews.source;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 등록된 소스 하나의 운영 설정과 프로필 정보 — docs/specs/source.md §3.
 * <p>
 * 프로필을 얻지 못하면 {@code name} · {@code homepage} · {@code termsReviewedAt} 은 null 이다.
 */
public record SourceView(SourceId id, @Nullable String presetKey, boolean enabled,
		@Nullable Integer pollIntervalMinutes, ProfileState profileState, @Nullable String name,
		@Nullable URI homepage, @Nullable LocalDate termsReviewedAt, List<FeedView> feeds) {

	public SourceView {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(profileState, "profileState");
		feeds = List.copyOf(feeds);
	}

}
