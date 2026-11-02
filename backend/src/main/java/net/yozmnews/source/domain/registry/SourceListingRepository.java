package net.yozmnews.source.domain.registry;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedStatus;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.poll.FeedPollState;
import org.jspecify.annotations.Nullable;

/**
 * 운영자 목록의 조회 — 소스 · 피드의 운영 설정 행과 피드의 수집 상태 행을 읽기만 한다. docs/specs/source.md §4.
 */
public interface SourceListingRepository {

	/** 모든 소스 — 소스 ID 순 */
	List<SourceRow> findAllSources();

	/** 모든 피드와 그 수집 상태 — 소스 ID, 피드 ID 순 */
	List<FeedRow> findAllFeeds();

	/**
	 * 소스 행 — 프리셋 키와 운영자 원문 가운데 하나만 있다.
	 * @param pollIntervalMinutes 운영자 소스 주기(분) — 정하지 않았으면 null
	 * @param hiddenAt 숨긴 시각 — 숨기지 않았으면 null
	 */
	record SourceRow(SourceId id, @Nullable String presetKey, @Nullable String profile, boolean enabled,
			@Nullable Integer pollIntervalMinutes, @Nullable Instant hiddenAt) {

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
	 * @param hiddenAt 숨긴 시각 — 숨기지 않았으면 null
	 * @param poll 수집 상태 — 수집 상태 행이 없을 때만 null 이다(등록 · 기동 대조가 두 행을 함께 만든다 — source-R3)
	 */
	record FeedRow(FeedId id, SourceId sourceId, String feedKey, boolean enabled,
			@Nullable Integer pollIntervalMinutes, @Nullable Instant hiddenAt, @Nullable PollRow poll) {

		public FeedRow {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(sourceId, "sourceId");
			Objects.requireNonNull(feedKey, "feedKey");
		}

	}

	/**
	 * 피드의 수집 상태 행 — 판정 입력과 피드 건강 검사가 저장한 상태.
	 * @param statusChangedAt 피드 건강 검사가 상태를 마지막으로 바꾼 시각 — 바꾼 적이 없으면 null
	 */
	record PollRow(FeedPollState state, FeedStatus status, @Nullable Instant statusChangedAt) {

		public PollRow {
			Objects.requireNonNull(state, "state");
			Objects.requireNonNull(status, "status");
		}

	}

}
