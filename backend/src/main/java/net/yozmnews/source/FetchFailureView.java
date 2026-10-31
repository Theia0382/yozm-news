package net.yozmnews.source;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 피드를 받지 못한 이유 — docs/specs/source.md §3.
 * <p>
 * {@code message} 는 운영자용 문구이고 응답 본문을 옮기지 않는다(source-R37). {@code problem} 은
 * {@code UNREADABLE} 일 때만 있다.
 */
public record FetchFailureView(FetchFailureReason reason, FailurePermanence permanence, String message,
		@Nullable Integer httpStatus, @Nullable DocumentProblemView problem) {

	public FetchFailureView {
		Objects.requireNonNull(reason, "reason");
		Objects.requireNonNull(permanence, "permanence");
		Objects.requireNonNull(message, "message");
	}

}
