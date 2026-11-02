package net.yozmnews.source;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 운영자가 고를 수 있는 프리셋 하나 — docs/specs/source.md §3.
 * <p>
 * {@code sourceId} 는 그 프리셋을 참조하는 소스이고(숨긴 소스 포함), 아직 등록하지 않았으면 null 이다. {@code sourceHidden} 은 그 소스를
 * 숨겼는지다 — 숨긴 소스의 프리셋은 다시 등록하지 않고 다시 보인다.
 */
public record PresetView(String key, String name, URI homepage, LocalDate termsReviewedAt, int feedCount,
		@Nullable SourceId sourceId, boolean sourceHidden) {

	public PresetView {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(homepage, "homepage");
		Objects.requireNonNull(termsReviewedAt, "termsReviewedAt");
	}

}
