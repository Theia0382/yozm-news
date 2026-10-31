package net.yozmnews.admin.adapter.in.web;

import java.net.URI;
import java.time.LocalDate;

import net.yozmnews.source.PresetView;
import org.jspecify.annotations.Nullable;

/**
 * 프리셋 하나 — docs/specs/admin.md §3.
 * <p>
 * {@code sourceId} 는 그 프리셋을 참조하는 소스이고, 아직 등록하지 않았으면 null 이다.
 */
record PresetResponse(String key, String name, URI homepage, LocalDate termsReviewedAt, int feedCount,
		@Nullable Long sourceId) {

	static PresetResponse of(PresetView preset) {
		return new PresetResponse(preset.key(), preset.name(), preset.homepage(), preset.termsReviewedAt(),
				preset.feedCount(), (preset.sourceId() != null) ? preset.sourceId().value() : null);
	}

}
