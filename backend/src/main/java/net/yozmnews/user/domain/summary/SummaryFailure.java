package net.yozmnews.user.domain.summary;

/**
 * 정리 호출의 실패 코드 — 시도 기록과 로그에 이 이름으로 남는다. docs/specs/user.md §5
 */
public enum SummaryFailure {

	/** 공급자가 키를 받지 않았다 */
	INVALID_KEY(true),

	/** 키에 그 모델을 쓸 권한이 없다 */
	PERMISSION_DENIED(true),

	/** 한도 · 잔액이 바닥났다 */
	QUOTA_EXHAUSTED(true),

	/** 공급자의 일시 장애 */
	PROVIDER_UNAVAILABLE(false),

	/** 예상 밖의 실패 */
	UNEXPECTED(false),

	/** 응답을 출력 서술대로 읽지 못했다 */
	UNREADABLE_OUTPUT(false),

	/** 응답이 출력 상한에서 잘렸다 */
	OUTPUT_TRUNCATED(false),

	/** 모델이 쓰기를 거절했다 */
	OUTPUT_REFUSED(false);

	private final boolean key;

	SummaryFailure(boolean key) {
		this.key = key;
	}

	/** 키 쪽의 실패인가 — 같은 실행의 다른 흐름도 같은 까닭으로 실패하므로 실행을 멈춘다 */
	public boolean ofKey() {
		return this.key;
	}

}
