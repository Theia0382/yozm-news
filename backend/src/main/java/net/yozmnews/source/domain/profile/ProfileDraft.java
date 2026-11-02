package net.yozmnews.source.domain.profile;

import java.util.List;
import java.util.Map;

import net.yozmnews.platform.StrictYamlBinding.Located;
import net.yozmnews.platform.TextLocation;
import net.yozmnews.platform.YamlPath;
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

	/** 소스 정보 초안 */
	record SourceDraft(YamlPath path, TextLocation location, @Nullable Located<String> name,
			@Nullable Located<String> homepage, @Nullable Located<String> language) {

	}

	/** 피드 초안 */
	record FeedDraft(YamlPath path, TextLocation location, @Nullable Located<String> key,
			@Nullable Located<String> url, @Nullable Located<String> section,
			@Nullable Located<Integer> pollIntervalMinutes) {

	}

	/** 사전 보정 초안 */
	record PreCorrectionDraft(YamlPath path, TextLocation location, @Nullable Located<String> rule,
			@Nullable Located<String> charset) {

	}

	/** 날짜 해석 초안 */
	record DatesDraft(YamlPath path, TextLocation location, @Nullable Located<List<Located<String>>> formats,
			@Nullable Located<String> timeZone) {

	}

}
