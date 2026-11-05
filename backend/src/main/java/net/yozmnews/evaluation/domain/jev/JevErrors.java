package net.yozmnews.evaluation.domain.jev;

import net.yozmnews.evaluation.EvaluatorFailureKind;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jev 오류 응답의 갈래와 운영자용 문구 — docs/specs/evaluation.md §4 Jev 대응표.
 * <p>
 * 오류 대부분은 기사와 무관한 원인(키 · 모델 · 요청 모양)이라 평가기 사용 불가다 — 다른 기사를 보내도 같다. 기사 탓일 수 있는 것은 문맥
 * 초과 하나이고, 응답 본문으로는 기사(state) 탓인지 질문 쪽(평가는 체계, 사건 소속 판정은 후보 서술) 탓인지 가를 수 없어 확인 요청으로
 * 가른다({@link #afterProbe}).
 * <p>
 * 오류 본문의 {@code detail} 은 객체 · 배열 · 문자열 세 모양이다. 배열(검증 실패)은 원소마다 요청 본문을 되돌려주는 {@code input} 을
 * 갖는데, 문구는 그것을 읽지 않는다.
 */
public final class JevErrors {

	/** 문맥 초과의 {@code error_type} */
	public static final String CONTEXT_EXCEEDED = "max_tokens_exceeded";

	/** 문구 전체의 길이 상한(코드 포인트) */
	static final int MAX_MESSAGE_CODE_POINTS = 500;

	/** Jev 가 준 설명의 길이 상한(코드 포인트) */
	static final int MAX_DETAIL_CODE_POINTS = 200;

	/** {@code error_type} · 요청 ID · Retry-After 하나의 길이 상한(코드 포인트) */
	static final int MAX_TOKEN_CODE_POINTS = 80;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private JevErrors() {
	}

	/** 200 이 아닌 응답의 상태와 본문으로 갈래를 정한다 */
	public static JevOutcome classify(int status, String body) {
		if (status == 400 && CONTEXT_EXCEEDED.equals(detail(body).errorType())) {
			return new JevOutcome.ContextExceeded();
		}
		if (status == 408 || status == 429 || (status >= 500 && status <= 599)) {
			return new JevOutcome.Failed(EvaluatorFailureKind.TRANSIENT);
		}
		return new JevOutcome.Failed(EvaluatorFailureKind.EVALUATOR_UNAVAILABLE);
	}

	/**
	 * 문맥 초과 확인 요청(같은 질문 · 빈 state)의 응답으로 원래 요청의 갈래를 정한다 — 받으면 기사 탓, 그것도 넘으면 질문 쪽 탓(평가기 사용
	 * 불가), 그 밖은 그 응답의 갈래(원인을 가르지 못했으므로 다음 시도에서 다시 가른다)
	 */
	public static EvaluatorFailureKind afterProbe(int probeStatus, String probeBody) {
		if (probeStatus == 200) {
			return EvaluatorFailureKind.ARTICLE_REJECTED;
		}
		return switch (classify(probeStatus, probeBody)) {
			case JevOutcome.ContextExceeded exceeded -> EvaluatorFailureKind.EVALUATOR_UNAVAILABLE;
			case JevOutcome.Failed failed -> failed.kind();
		};
	}

	/**
	 * 운영자용 문구 — 무엇이 일어났는지, 상태 · {@code error_type} · Jev 의 설명 앞부분 · {@code Retry-After} · 요청 ID. 요청 본문 ·
	 * 응답 본문 원문은 담지 않는다. 바깥 값은 제어 문자를 공백으로 바꾸고 길이를 자른다.
	 */
	public static String message(String what, int status, String body, @Nullable String retryAfter,
			@Nullable String requestId) {
		Detail detail = detail(body);
		StringBuilder text = new StringBuilder(what).append(" — Jev 응답 ").append(status);
		if (detail.errorType() != null) {
			text.append(' ').append(clean(detail.errorType(), MAX_TOKEN_CODE_POINTS));
		}
		if (detail.message() != null) {
			text.append(": ").append(clean(detail.message(), MAX_DETAIL_CODE_POINTS));
		}
		if (retryAfter != null) {
			text.append(" · Retry-After ").append(clean(retryAfter, MAX_TOKEN_CODE_POINTS));
		}
		return clean(withRequestId(text.toString(), requestId), MAX_MESSAGE_CODE_POINTS);
	}

	/** 요청 ID 를 붙인 문구 — 없으면 그대로 */
	static String withRequestId(String text, @Nullable String requestId) {
		return (requestId == null) ? text : text + " (요청 " + clean(requestId, MAX_TOKEN_CODE_POINTS) + ")";
	}

	/**
	 * 바깥 문자열을 문구에 넣을 수 있게 고친다 — 제어 문자는 공백, 짝 없는 서로게이트는 U+FFFD 로 바꾸고 앞 {@code max} 코드 포인트만
	 * 남긴다(서로게이트 쌍을 가르지 않는다)
	 */
	static String clean(String text, int max) {
		StringBuilder result = new StringBuilder();
		int count = 0;
		for (int offset = 0; offset < text.length() && count < max; count++) {
			int codePoint = text.codePointAt(offset);
			offset += Character.charCount(codePoint);
			if (Character.isISOControl(codePoint)) {
				result.append(' ');
			}
			else if (Character.charCount(codePoint) == 1 && Character.isSurrogate((char) codePoint)) {
				result.append('\uFFFD');
			}
			else {
				result.appendCodePoint(codePoint);
			}
		}
		return result.toString();
	}

	private static Detail detail(String body) {
		JsonNode root;
		try {
			root = JSON.readTree(body);
		}
		catch (JacksonException ex) {
			return Detail.NONE;
		}
		if (root == null || !root.isObject()) {
			return Detail.NONE;
		}
		JsonNode detail = root.get("detail");
		if (detail == null) {
			return Detail.NONE;
		}
		if (detail.isString()) {
			return new Detail(null, detail.stringValue());
		}
		if (detail.isObject()) {
			return new Detail(string(detail.get("error_type")), string(detail.get("message")));
		}
		if (detail.isArray() && !detail.isEmpty() && detail.get(0).isObject()) {
			JsonNode first = detail.get(0);
			return new Detail(string(first.get("type")), string(first.get("msg")));
		}
		return Detail.NONE;
	}

	private static @Nullable String string(@Nullable JsonNode node) {
		return (node != null && node.isString()) ? node.stringValue() : null;
	}

	private record Detail(@Nullable String errorType, @Nullable String message) {

		static final Detail NONE = new Detail(null, null);

	}

}
