package net.yozmnews.source.domain.fetch;

/**
 * 응답 상태 코드의 규칙.
 */
public final class StatusRule {

	private StatusRule() {
	}

	/**
	 * @param status 응답 상태 코드 — 100~599 밖이면(예: Apache 가 받는 세 자리 999) 예상 밖 응답이다
	 * @param conditionalSent 이 요청에 조건부 헤더를 보냈는가 — 보내지 않았는데 온 304 는 예상 밖 응답이다
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
