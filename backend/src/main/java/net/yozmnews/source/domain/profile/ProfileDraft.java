package net.yozmnews.source.domain.profile;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.source.domain.profile.text.TextLocation;
import org.jspecify.annotations.Nullable;

/**
 * 형(type) 검사를 마친 프로필 초안 — 모든 항목이 선택이고, 값마다 경로와 위치가 붙어 있다. 의미 검사는
 * {@link ProfileValidator} 가 한다.
 */
record ProfileDraft(TextLocation location, @Nullable SourceDraft source, @Nullable Located<String> termsReviewedAt,
		@Nullable Located<List<FeedDraft>> feeds, @Nullable Located<Integer> pollIntervalMinutes,
		@Nullable Located<List<PreCorrectionDraft>> preCorrections,
		@Nullable Located<Map<ItemField, Located<List<Located<String>>>>> items,
		@Nullable Located<Map<String, Located<String>>> namespaces, @Nullable DatesDraft dates) {

	/** 경로와 위치를 단 값 */
	record Located<T>(T value, ProfilePath path, TextLocation location) {

		Located {
			Objects.requireNonNull(value, "value");
			Objects.requireNonNull(path, "path");
			Objects.requireNonNull(location, "location");
		}

	}

	record SourceDraft(ProfilePath path, TextLocation location, @Nullable Located<String> name,
			@Nullable Located<String> homepage, @Nullable Located<String> language) {

	}

	record FeedDraft(ProfilePath path, TextLocation location, @Nullable Located<String> key,
			@Nullable Located<String> url, @Nullable Located<String> section,
			@Nullable Located<Integer> pollIntervalMinutes) {

	}

	record PreCorrectionDraft(ProfilePath path, TextLocation location, @Nullable Located<String> rule,
			@Nullable Located<String> charset) {

	}

	record DatesDraft(ProfilePath path, TextLocation location, @Nullable Located<List<Located<String>>> formats,
			@Nullable Located<String> timeZone) {

	}

}
