package net.yozmnews.source.domain.fetch;

import java.util.Objects;

import net.yozmnews.source.FailurePermanence;
import net.yozmnews.source.FetchFailureReason;
import net.yozmnews.source.domain.feed.DocumentProblem;
import org.jspecify.annotations.Nullable;

/**
 * 수집 실패 — source-R37.
 *
 * @param httpStatus 실패를 정한 응답의 상태 코드 — 응답이 없었으면 null
 * @param problem 정규화가 문서를 읽지 못한 문제 — {@link FetchFailureReason#UNREADABLE} 일 때만 있다
 */
public record FetchFailure(FetchFailureReason reason, String message, @Nullable Integer httpStatus,
		@Nullable DocumentProblem problem) {

	/**
	 * 이유 · 문제 · 상태 코드가 맞는지 확인한다.
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

	/** 상태 코드로 정해진 실패를 만든다 — 문구 끝에 {@code (HTTP 404)} 처럼 코드를 붙인다 */
	public static FetchFailure status(FetchFailureReason reason, int status) {
		return new FetchFailure(reason, description(reason) + " (HTTP " + status + ")", status, null);
	}

	/** 정규화가 문서를 읽지 못한 실패를 만든다 — 문구는 문제의 문구다 */
	public static FetchFailure unreadable(DocumentProblem problem) {
		return new FetchFailure(FetchFailureReason.UNREADABLE, problem.message(), null, problem);
	}

	/** 응답 없이 정해진 실패를 만든다 — 덧붙임이 비었으면 이유의 문구만 쓴다 */
	public static FetchFailure of(FetchFailureReason reason, String detail) {
		String message = detail.isBlank() ? description(reason) : description(reason) + ": " + detail;
		return new FetchFailure(reason, message, null, null);
	}

	/** 운영자용 한국어 문구의 머리를 돌려준다 */
	private static String description(FetchFailureReason reason) {
		return switch (reason) {
			case TIMEOUT -> "수집 시간 상한을 넘었다";
			case CONNECTION_FAILED -> "연결하지 못했거나 연결이 끊겼다";
			case NAME_NOT_RESOLVED -> "호스트 이름을 해석하지 못했다";
			case SERVER_ERROR -> "서버 오류 응답";
			case THROTTLED -> "요청 제한 응답";
			case ACCESS_DENIED -> "접근 거부 응답";
			case NOT_FOUND -> "피드가 없다는 응답";
			case CLIENT_ERROR -> "요청 오류 응답";
			case UNEXPECTED_STATUS -> "예상하지 못한 응답";
			case TOO_MANY_REDIRECTS -> "리다이렉트가 상한을 넘었다";
			case INVALID_REDIRECT -> "리다이렉트 대상을 읽을 수 없다";
			case UNSUPPORTED_URL -> "요청할 수 없는 주소다";
			case BLOCKED_ADDRESS -> "내부 주소로 가는 요청을 막았다";
			case TLS_FAILURE -> "TLS 연결에 실패했다";
			case TOO_LARGE -> "응답 본문이 크기 상한을 넘었다";
			case UNREADABLE -> "피드 문서를 읽지 못했다";
		};
	}

}
