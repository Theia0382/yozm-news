package net.yozmnews.source.domain.fetch;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

import net.yozmnews.source.domain.feed.FeedNormalization;
import org.jspecify.annotations.Nullable;

/**
 * 한 번의 수집 결과 — 성공 · 변경 없음 · 실패
 */
public sealed interface FetchResult {

	/**
	 * 200 — 받은 문서를 정규화했다.
	 *
	 * @param normalization 정규화 결과 — 기사가 0건일 수 있다
	 * @param state 이 응답이 준 조건부 요청 값
	 * @param fetchedAt 본문을 다 받은 시각 — 정규화의 받은 시각과 같다
	 * @param movedTo 처음 URL 에서 끊기지 않고 이어진 영구 이동(301 · 308)의 마지막 대상 — 프로필이 앞으로 쓸 주소. 없으면
	 * null
	 */
	record Fetched(FeedNormalization.Normalized normalization, FetchState state, Instant fetchedAt,
			@Nullable URI movedTo) implements FetchResult {

		public Fetched {
			Objects.requireNonNull(normalization, "normalization");
			Objects.requireNonNull(state, "state");
			Objects.requireNonNull(fetchedAt, "fetchedAt");
		}

	}

	/**
	 * 304 — 바뀌지 않았다.
	 *
	 * @param state 이전 상태에 304 가 준 값을 반영한 조건부 요청 값
	 * @param fetchedAt 응답을 받은 시각
	 * @param movedTo {@link Fetched#movedTo} 와 같다
	 */
	record NotModified(FetchState state, Instant fetchedAt, @Nullable URI movedTo) implements FetchResult {

		public NotModified {
			Objects.requireNonNull(state, "state");
			Objects.requireNonNull(fetchedAt, "fetchedAt");
		}

	}

	/**
	 * 실패했다.
	 *
	 * @param failure 이유와 분류
	 * @param attemptedAt 실패를 확정한 시각
	 */
	record Failed(FetchFailure failure, Instant attemptedAt) implements FetchResult {

		public Failed {
			Objects.requireNonNull(failure, "failure");
			Objects.requireNonNull(attemptedAt, "attemptedAt");
		}

	}

}
