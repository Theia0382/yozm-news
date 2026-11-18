package net.yozmnews.digest.domain.digest;

/**
 * 실패한 다이제스트의 오류 코드 · docs/specs/digest.md §5 · digest-R8
 * <p>
 * 관문이 가른 실패 종류는 {@code GenerationFailures} 가 옮긴다 — 사용자에게 보일 문구는 코드로 고른다.
 */
public enum DigestFailure {

	/** 출력을 읽지 못했다 — 다시 물어도 읽지 못했다 */
	UNREADABLE_OUTPUT,

	/** 검증 뒤 인용이 하나도 남지 않았다 */
	NO_CITATION,

	/** 쓸 모델 공급자의 저장한 키가 없었다 — 공급자에 보내지 않았다 */
	KEY_MISSING,

	/** 공급자가 키를 무효로 답했다(관문의 {@code INVALID_KEY}) */
	KEY_INVALID,

	/** 키는 맞지만 그 모델을 쓸 권한이 없다(관문의 {@code PERMISSION_DENIED}) */
	PERMISSION_DENIED,

	/** 잔액 · 지출 한도가 소진됐다(관문의 {@code QUOTA_EXHAUSTED}) */
	QUOTA_EXHAUSTED,

	/** 일시 장애가 다시 보내도 이어졌다(관문의 {@code TRANSIENT}) */
	PROVIDER_UNAVAILABLE,

	/** 공급자가 예상 밖의 답을 했다(관문의 {@code UNEXPECTED}) */
	PROVIDER_UNEXPECTED,

	/** 출력이 출력 토큰 상한에 닿아 잘렸다 — 다시 묻지 않는다 */
	OUTPUT_TRUNCATED,

	/** 모델이 거절했다 — 다시 묻지 않는다 */
	OUTPUT_REFUSED,

	/** 생성을 시작할 때 쓸 모델이 없거나 계정이 없었다 — 생성기를 부르지 않았다 */
	NO_USABLE_MODEL,

	/** 정한 시간 안에 생성이 끝나지 않았다 — 시작하지 못했거나 생성 중에 멈췄다 */
	INTERRUPTED

}
