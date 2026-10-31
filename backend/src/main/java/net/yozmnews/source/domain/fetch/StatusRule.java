package net.yozmnews.source.domain.fetch;

import net.yozmnews.source.FetchFailureReason;

/**
 * 응답 상태 코드의 규칙 — docs/specs/source.md §4 · source-R23 · source-R24
 */
public final class StatusRule {

	private StatusRule() {
	}

	/**
	 * 응답 상태 코드를 분류한다.
	 * @param status 응답 상태 코드 — Apache 는 세 자리 999 까지 받는다
	 * @param conditionalSent 이 요청에 조건부 헤더를 보냈는가
	 */
	public static StatusOutcome classify(int status, boolean conditionalSent) {
		return switch (status) {
			case 200 -> new StatusOutcome.Body();
			case 304 -> conditionalSent ? new StatusOutcome.NotModified()
					: new StatusOutcome.Failure(FetchFailureReason.UNEXPECTED_STATUS);
			case 301, 308 -> new StatusOutcome.Redirect(true);
			case 302, 303, 307 -> new StatusOutcome.Redirect(false);
			case 401, 403 -> new StatusOutcome.Failure(FetchFailureReason.ACCESS_DENIED);
			case 404, 410 -> new StatusOutcome.Failure(FetchFailureReason.NOT_FOUND);
			case 408, 429 -> new StatusOutcome.Failure(FetchFailureReason.THROTTLED);
			default -> new StatusOutcome.Failure(byRange(status));
		};
	}

	private static FetchFailureReason byRange(int status) {
		if (status >= 400 && status <= 499) {
			return FetchFailureReason.CLIENT_ERROR;
		}
		if (status >= 500 && status <= 599) {
			return FetchFailureReason.SERVER_ERROR;
		}
		return FetchFailureReason.UNEXPECTED_STATUS;
	}

}
