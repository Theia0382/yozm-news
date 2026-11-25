package net.yozmnews.report;

/**
 * 리포트 실패 코드 · docs/specs/report.md §3 · report-R8 · report-R24
 */
public enum ReportFailure {

	/** 끝까지 쓴 응답을 서술대로 읽지 못했다 — 다시 물어도 읽지 못했다 */
	UNREADABLE_OUTPUT,

	/** 응답이 출력 토큰 상한에 닿아 잘렸다 — 다시 묻지 않는다 */
	OUTPUT_TRUNCATED,

	/** 모델이 출력을 거절했다 — 다시 묻지 않는다 */
	OUTPUT_REFUSED,

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

	/** 쓸 모델이 없었다 — 유효한 키가 있는 공급자의 허용 모델이 없거나 계정을 쓸 수 없어 시작하지 않았다 */
	NO_USABLE_MODEL,

	/** 시작 기한 · 임대 안에 생성이 끝나지 않았다 — 다시 돌리지 않는다 */
	INTERRUPTED

}
