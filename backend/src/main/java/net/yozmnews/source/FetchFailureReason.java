package net.yozmnews.source;

/**
 * 수집 실패의 이유 — docs/specs/source.md §4 · source-R24
 */
public enum FetchFailureReason {

	TIMEOUT,
	CONNECTION_FAILED,
	NAME_NOT_RESOLVED,
	SERVER_ERROR,
	THROTTLED,
	/** 대개 봇 차단이라 사람이 풀어야 한다 */
	ACCESS_DENIED,
	NOT_FOUND,
	CLIENT_ERROR,
	UNEXPECTED_STATUS,
	TOO_MANY_REDIRECTS,
	INVALID_REDIRECT,
	UNSUPPORTED_URL,
	BLOCKED_ADDRESS,
	TLS_FAILURE,
	TOO_LARGE,
	UNREADABLE;

	/** 일시 · 영구 분류를 돌려준다 — 정본 */
	public FailurePermanence permanence() {
		return switch (this) {
			case TIMEOUT, CONNECTION_FAILED, NAME_NOT_RESOLVED, SERVER_ERROR, THROTTLED -> FailurePermanence.TEMPORARY;
			case ACCESS_DENIED, NOT_FOUND, CLIENT_ERROR, UNEXPECTED_STATUS, TOO_MANY_REDIRECTS, INVALID_REDIRECT,
					UNSUPPORTED_URL, BLOCKED_ADDRESS, TLS_FAILURE, TOO_LARGE, UNREADABLE ->
				FailurePermanence.PERMANENT;
		};
	}

}
