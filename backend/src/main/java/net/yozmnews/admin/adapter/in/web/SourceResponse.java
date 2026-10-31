package net.yozmnews.admin.adapter.in.web;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import net.yozmnews.source.FeedView;
import net.yozmnews.source.ProfileState;
import net.yozmnews.source.SourceView;
import org.jspecify.annotations.Nullable;

/**
 * 등록된 소스 하나 — docs/specs/admin.md §3.
 * <p>
 * 프로필을 얻지 못하면({@code profileState} 가 {@code AVAILABLE} 이 아님) 이름 · 홈페이지 · 검토일은 null 이다.
 */
record SourceResponse(long id, @Nullable String presetKey, boolean enabled, @Nullable Integer pollIntervalMinutes,
		ProfileState profileState, @Nullable String name, @Nullable URI homepage, @Nullable LocalDate termsReviewedAt,
		List<FeedResponse> feeds) {

	static SourceResponse of(SourceView source) {
		return new SourceResponse(source.id().value(), source.presetKey(), source.enabled(),
				source.pollIntervalMinutes(), source.profileState(), source.name(), source.homepage(),
				source.termsReviewedAt(), source.feeds().stream().map(FeedResponse::of).toList());
	}

	/**
	 * 소스의 피드 하나 — 프로필에 그 피드가 없으면({@code inProfile} 거짓) URL · 섹션 · 실제 주기는 null 이다.
	 * @param effectivePollIntervalMinutes 실제 주기(분) — 운영 설정과 프로필의 권장값에서 정한 값
	 */
	record FeedResponse(long id, String feedKey, boolean enabled, @Nullable Integer pollIntervalMinutes,
			boolean inProfile, @Nullable URI url, @Nullable String section,
			@Nullable Integer effectivePollIntervalMinutes) {

		static FeedResponse of(FeedView feed) {
			return new FeedResponse(feed.id().value(), feed.feedKey(), feed.enabled(), feed.pollIntervalMinutes(),
					feed.inProfile(), feed.url(), feed.section(), feed.effectivePollIntervalMinutes());
		}

	}

}
