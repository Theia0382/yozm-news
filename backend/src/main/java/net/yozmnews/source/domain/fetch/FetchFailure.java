package net.yozmnews.source.domain.fetch;

import java.util.Objects;

import net.yozmnews.source.domain.feed.DocumentProblem;
import org.jspecify.annotations.Nullable;

/**
 * 수집 실패. 문구는 운영자에게 보이는 한국어이고 응답 본문을 옮기지 않는다 — 인용하는 것은 상태 코드 · URL · 주소 · 상한
 * 값뿐이다.
 *
 * @param reason 이유
 * @param message 문구
 * @param httpStatus 실패를 정한 응답의 상태 코드 — 응답이 없었으면 null
 * @param problem 정규화가 문서를 읽지 못한 문제 — {@link FetchFailureReason#UNREADABLE} 일 때만 있다
 */
public record FetchFailure(FetchFailureReason reason, String message, @Nullable Integer httpStatus,
		@Nullable DocumentProblem problem) {

	/**
	 * @throws IllegalArgumentException 문제의 유무가 이유와 맞지 않거나, 상태 코드가 100~999 밖일 때
	 */
	public FetchFailure {
		Objects.requireNonNull(reason, "reason");
		Objects.requireNonNull(message, "message");
		if ((reason == FetchFailureReason.UNREADABLE) != (problem != null)) {
			throw new IllegalArgumentException("문서 문제는 UNREADABLE 에만 있다: " + reason);
		}
		if (httpStatus != null && (httpStatus < 100 || httpStatus > 999)) {
			throw new IllegalArgumentException("상태 코드는 세 자리다: " + httpStatus);
		}
	}

	public FailurePermanence permanence() {
		return this.reason.permanence();
	}

	/** 상태 코드로 정해진 실패 — 문구 끝에 {@code (HTTP 404)} 처럼 코드를 붙인다 */
	public static FetchFailure status(FetchFailureReason reason, int status) {
		return new FetchFailure(reason, reason.description() + " (HTTP " + status + ")", status, null);
	}

	/** 정규화가 문서를 읽지 못했다 — 문구는 문제의 문구다 */
	public static FetchFailure unreadable(DocumentProblem problem) {
		return new FetchFailure(FetchFailureReason.UNREADABLE, problem.message(), null, problem);
	}

	/** 응답 없이 정해진 실패 — 덧붙임이 비었으면 이유의 문구만 */
	public static FetchFailure of(FetchFailureReason reason, String detail) {
		String message = detail.isBlank() ? reason.description() : reason.description() + ": " + detail;
		return new FetchFailure(reason, message, null, null);
	}

}
