package net.yozmnews.source.domain.registry;

import java.util.List;
import java.util.Objects;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import org.jspecify.annotations.Nullable;

/**
 * 운영자 목록의 조회 — 소스 · 피드의 운영 설정 행을 읽기만 한다. docs/specs/source.md §4.
 */
public interface SourceListingRepository {

	/** 모든 소스 — 소스 ID 순 */
	List<SourceRow> findAllSources();

	/** 모든 피드 — 소스 ID, 피드 ID 순 */
	List<FeedRow> findAllFeeds();

	/**
	 * 소스 행 — 프리셋 키와 운영자 원문 가운데 하나만 있다.
	 * @param pollIntervalMinutes 운영자 소스 주기(분) — 정하지 않았으면 null
	 */
	record SourceRow(SourceId id, @Nullable String presetKey, @Nullable String profile, boolean enabled,
			@Nullable Integer pollIntervalMinutes) {

		public SourceRow {
			Objects.requireNonNull(id, "id");
			if ((presetKey == null) == (profile == null)) {
				throw new IllegalArgumentException("소스는 프리셋 키와 운영자 프로필 가운데 하나만 갖는다: " + id.value());
			}
		}

	}

	/**
	 * 피드 행.
	 * @param pollIntervalMinutes 운영자 피드 주기(분) — 정하지 않았으면 null
	 */
	record FeedRow(FeedId id, SourceId sourceId, String feedKey, boolean enabled,
			@Nullable Integer pollIntervalMinutes) {

		public FeedRow {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(sourceId, "sourceId");
			Objects.requireNonNull(feedKey, "feedKey");
		}

	}

}
