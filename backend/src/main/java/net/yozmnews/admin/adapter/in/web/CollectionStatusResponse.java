package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import net.yozmnews.article.EntryBucket;
import net.yozmnews.article.EntryCounts;
import net.yozmnews.article.EntryStatistics;
import net.yozmnews.article.FeedEntryStatistics;
import net.yozmnews.source.FeedHealthView;
import net.yozmnews.source.FeedStatus;
import net.yozmnews.source.FeedStatusCriteria;
import net.yozmnews.source.FeedView;
import net.yozmnews.source.LastFailureView;
import net.yozmnews.source.ProfileState;
import net.yozmnews.source.SourceView;
import org.jspecify.annotations.Nullable;

/**
 * 수집 현황 — 집계 기간, 피드 상태의 기준값, 소스마다 합계와 피드 행. docs/specs/admin.md §3.
 * <p>
 * 숨긴 소스 · 숨긴 피드는 싣지 않는다. 소스 행의 개수는 그 소스의 숨기지 않은 피드 전부(꺼진 피드 · 프로필에 없는 피드 포함)의 칸별
 * 합이다. 비율은 개수와 함께 {@code article} 이 낸 값을 옮긴다(article-R14).
 *
 * @param from 집계 기간의 시작(포함)
 * @param to 집계 기간의 끝(제외) — 집계한 시각
 */
record CollectionStatusResponse(Instant from, Instant to, StatusCriteriaResponse criteria,
		List<SourceCollectionResponse> sources) {

	static CollectionStatusResponse of(List<SourceView> sources, EntryStatistics statistics,
			FeedStatusCriteria criteria) {
		return new CollectionStatusResponse(statistics.from(), statistics.to(), StatusCriteriaResponse.of(criteria),
				sources.stream().map(source -> SourceCollectionResponse.of(source, statistics)).toList());
	}

	/**
	 * 피드 상태의 판정 기준값과 재판정 간격.
	 * @param staleAfterHours 새 기사가 이만큼(시간) 없으면 {@code STALE}
	 * @param recheckIntervalMinutes 피드 건강 검사가 다시 판정하는 간격(분)
	 */
	record StatusCriteriaResponse(int degradedAfterFailures, int degradedAfterPermanentFailures, long staleAfterHours,
			long recheckIntervalMinutes) {

		static StatusCriteriaResponse of(FeedStatusCriteria criteria) {
			return new StatusCriteriaResponse(criteria.degradedAfterFailures(),
					criteria.degradedAfterPermanentFailures(), criteria.staleAfter().toHours(),
					criteria.recheckInterval().toMinutes());
		}

	}

	/**
	 * 소스 하나 — 운영 설정 일부, 피드 전부의 합계와 칸별 합, 피드 행. 프로필을 얻지 못하면 이름은 null 이다.
	 */
	record SourceCollectionResponse(long id, @Nullable String name, @Nullable String presetKey, boolean enabled,
			ProfileState profileState, EntryMetricsResponse total, List<EntryBucketResponse> buckets,
			List<FeedCollectionResponse> feeds) {

		static SourceCollectionResponse of(SourceView source, EntryStatistics statistics) {
			FeedEntryStatistics combined = statistics
				.combined(source.feeds().stream().map(FeedView::id).toList());
			return new SourceCollectionResponse(source.id().value(), source.name(), source.presetKey(),
					source.enabled(), source.profileState(), EntryMetricsResponse.of(combined.total()),
					EntryBucketResponse.of(statistics.buckets(), combined),
					source.feeds().stream().map(feed -> FeedCollectionResponse.of(feed, statistics)).toList());
		}

	}

	/**
	 * 피드 하나 — 켜짐, 수집 상태, 합계와 칸별 개수. 프로필에 그 피드가 없으면({@code inProfile} 거짓) 섹션은 null 이다.
	 * <p>
	 * 상태는 피드 건강 검사가 저장한 값이고 {@code statusChangedAt} 은 그것을 마지막으로 바꾼 시각이다(바꾼 적이 없으면 null).
	 */
	record FeedCollectionResponse(long id, String feedKey, @Nullable String section, boolean enabled,
			boolean inProfile, FeedStatus status, @Nullable Instant statusChangedAt, @Nullable Instant lastSuccessAt,
			int consecutiveFailures, int consecutivePermanentFailures, @Nullable LastFailureView lastFailure,
			@Nullable Instant lastNewItemAt, @Nullable Instant latestPublishedAt, EntryMetricsResponse total,
			List<EntryBucketResponse> buckets) {

		static FeedCollectionResponse of(FeedView feed, EntryStatistics statistics) {
			FeedHealthView health = feed.health();
			FeedEntryStatistics counts = statistics.feed(feed.id());
			return new FeedCollectionResponse(feed.id().value(), feed.feedKey(), feed.section(), feed.enabled(),
					feed.inProfile(), health.status(), health.statusChangedAt(), health.lastSuccessAt(),
					health.consecutiveFailures(), health.consecutivePermanentFailures(), health.lastFailure(),
					health.lastNewItemAt(), health.latestPublishedAt(), EntryMetricsResponse.of(counts.total()),
					EntryBucketResponse.of(statistics.buckets(), counts));
		}

	}

	/**
	 * 엔트리 개수와 비율 — 엔트리가 없으면 비율은 null 이다(0 이 아니다).
	 */
	record EntryMetricsResponse(int entries, int duplicates, int urlMatches, int titleMatches, int estimated,
			int guidReuses, @Nullable Double duplicateRate, @Nullable Double estimatedRate,
			@Nullable Double titleMatchRate) {

		static EntryMetricsResponse of(EntryCounts counts) {
			return new EntryMetricsResponse(counts.entries(), counts.duplicates(), counts.urlMatches(),
					counts.titleMatches(), counts.estimated(), counts.guidReuses(), counts.duplicateRate(),
					counts.estimatedRate(), counts.titleMatchRate());
		}

	}

	/**
	 * 집계 칸 하나 — 시작 포함, 끝 제외.
	 */
	record EntryBucketResponse(Instant from, Instant to, EntryMetricsResponse metrics) {

		/** 칸과 칸별 개수를 같은 순서로 짝짓는다 */
		static List<EntryBucketResponse> of(List<EntryBucket> buckets, FeedEntryStatistics counts) {
			List<EntryBucketResponse> responses = new ArrayList<>(buckets.size());
			for (int i = 0; i < buckets.size(); i++) {
				EntryBucket bucket = buckets.get(i);
				responses.add(new EntryBucketResponse(bucket.from(), bucket.to(),
						EntryMetricsResponse.of(counts.buckets().get(i))));
			}
			return responses;
		}

	}

}
