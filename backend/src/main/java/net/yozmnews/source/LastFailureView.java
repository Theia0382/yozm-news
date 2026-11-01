package net.yozmnews.source;

import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 피드의 마지막 수집 실패 — docs/specs/source.md §3 · source-R26.
 * <p>
 * {@code message} 는 운영자용 문구이고 응답 본문을 옮기지 않는다(source-R37). 분류는 이유가 정한다
 * ({@link FetchFailureReason#permanence()}).
 *
 * @param httpStatus 실패를 정한 응답의 상태 코드 — 응답이 없었으면 null
 * @param at 실패를 확정한 시각
 */
public record LastFailureView(FetchFailureReason reason, FailurePermanence permanence, String message,
		@Nullable Integer httpStatus, Instant at) {

	public LastFailureView {
		Objects.requireNonNull(reason, "reason");
		Objects.requireNonNull(permanence, "permanence");
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(at, "at");
	}

}
