package net.yozmnews.source;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 운영자가 고를 수 있는 프리셋 하나 — docs/specs/source.md §3.
 * <p>
 * {@code sourceId} 는 그 프리셋을 참조하는 소스이고, 아직 등록하지 않았으면 null 이다.
 */
public record PresetView(String key, String name, URI homepage, LocalDate termsReviewedAt, int feedCount,
		@Nullable SourceId sourceId) {

	public PresetView {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(homepage, "homepage");
		Objects.requireNonNull(termsReviewedAt, "termsReviewedAt");
	}

}
