package net.yozmnews.source.domain.fetch;

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

	/** 운영자용 한국어 문구의 머리를 돌려준다 */
	String description() {
		return switch (this) {
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
