package net.yozmnews.source.domain.poll;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.source.FetchFailureReason;
import net.yozmnews.source.domain.fetch.FetchFailure;
import org.jspecify.annotations.Nullable;

/**
 * 피드의 마지막 수집 실패 — source-R26.
 *
 * @param message 운영자용 문구({@link FetchFailure#message()} 그대로 — 응답 본문을 옮기지 않는다)
 * @param httpStatus 실패를 정한 응답의 상태 코드 — 응답이 없었으면 null
 * @param at 실패를 확정한 시각
 */
public record LastError(FetchFailureReason reason, String message, @Nullable Integer httpStatus, Instant at) {

	public LastError {
		Objects.requireNonNull(reason, "reason");
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(at, "at");
	}

	public static LastError of(FetchFailure failure, Instant at) {
		return new LastError(failure.reason(), failure.message(), failure.httpStatus(), at);
	}

}
