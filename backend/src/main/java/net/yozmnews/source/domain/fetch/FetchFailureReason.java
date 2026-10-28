package net.yozmnews.source.domain.fetch;

/**
 * 수집 실패의 이유 — 닫힌 집합. 일시 · 영구 분류의 정본은 {@link #permanence()} 하나다.
 */
public enum FetchFailureReason {

	/** 한 번의 수집에 거는 마감을 넘었다 — 풀 대기 · 연결 · 응답 · 리다이렉트 · 본문 어디서든 */
	TIMEOUT,
	/** 연결하지 못했거나 연결이 끊겼다 — 그 밖의 입출력 오류 포함 */
	CONNECTION_FAILED,
	/** 호스트 이름을 해석하지 못했다 — "이름 없음"과 "해석기 장애"를 가르지 못해 일시로 본다 */
	NAME_NOT_RESOLVED,
	/** 5xx */
	SERVER_ERROR,
	/** 408 · 429 */
	THROTTLED,
	/** 401 · 403 — 대개 봇 차단이라 사람이 풀어야 한다 */
	ACCESS_DENIED,
	/** 404 · 410 */
	NOT_FOUND,
	/** 그 밖의 4xx */
	CLIENT_ERROR,
	/** 그 밖의 상태 코드, 조건부 헤더를 보내지 않았는데 온 304 */
	UNEXPECTED_STATUS,
	/** 리다이렉트 홉이 상한을 넘었다 */
	TOO_MANY_REDIRECTS,
	/** 리다이렉트의 {@code Location} 이 없거나 URI 로 읽을 수 없다 */
	INVALID_REDIRECT,
	/** 처음 URL 이나 리다이렉트 대상이 요청할 수 있는 주소가 아니다 */
	UNSUPPORTED_URL,
	/** 연결 주소가 차단 대역이다 */
	BLOCKED_ADDRESS,
	/** TLS 핸드셰이크 · 인증서 오류 */
	TLS_FAILURE,
	/** 본문이 크기 상한을 넘었다 */
	TOO_LARGE,
	/** 받은 문서를 정규화가 읽지 못했다 */
	UNREADABLE;

	public FailurePermanence permanence() {
		return switch (this) {
			case TIMEOUT, CONNECTION_FAILED, NAME_NOT_RESOLVED, SERVER_ERROR, THROTTLED -> FailurePermanence.TEMPORARY;
			case ACCESS_DENIED, NOT_FOUND, CLIENT_ERROR, UNEXPECTED_STATUS, TOO_MANY_REDIRECTS, INVALID_REDIRECT,
					UNSUPPORTED_URL, BLOCKED_ADDRESS, TLS_FAILURE, TOO_LARGE, UNREADABLE ->
				FailurePermanence.PERMANENT;
		};
	}

	/** 운영자용 한국어 문구의 머리 */
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
