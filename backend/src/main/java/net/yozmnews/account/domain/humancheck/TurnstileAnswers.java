package net.yozmnews.account.domain.humancheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.platform.GuardedAction;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turnstile 검증 응답의 판정 정본 — docs/specs/user.md §4 · user-R41.
 * <p>
 * 5xx 이거나 본문을 읽지 못하면(JSON 아님 · {@code success} 가 불리언이 아님) 닿지 못함이다. 그 밖은 본문의 {@code success} 로 가른다 —
 * 참이고 {@code action} 이 그 동작의 이름이면 통과, 동작이 다르면 거부, 거짓이면 오류 코드와 함께 거부(서비스 내부 오류 {@code internal-error}
 * 포함). 시험 비밀 키의 답({@code metadata.result_with_testing_key} 가 참)은 {@code action} 을 싣지 않으므로 동작을 대조하지 않고 통과다 —
 * 이 표시는 Cloudflare 가 시험 비밀 키에만 붙인다. {@code hostname} 은 보지 않는다 — 앱은 인스턴스의 바깥 주소를 모른다.
 */
public final class TurnstileAnswers {

	/** 운영 설정(비밀 키)이 틀렸다는 오류 코드 */
	static final Set<String> SECRET_ERRORS = Set.of("missing-input-secret", "invalid-input-secret");

	/** 동작 이름이 다른 토큰의 거부 까닭 */
	static final String ACTION_MISMATCH = "action-mismatch";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private TurnstileAnswers() {
	}

	/**
	 * 응답 하나를 판정한다.
	 * @param status HTTP 상태
	 * @param body 응답 본문
	 * @param expected 토큰이 나왔어야 하는 동작
	 */
	public static HumanCheckResult classify(int status, String body, GuardedAction expected) {
		Objects.requireNonNull(body, "body");
		Objects.requireNonNull(expected, "expected");
		if (status >= 500) {
			return new HumanCheckResult.Unavailable("Turnstile 검증 서비스가 상태 " + status + " 로 답했다");
		}
		JsonNode answer;
		try {
			answer = JSON.readTree(body);
		}
		catch (JacksonException ex) {
			return new HumanCheckResult.Unavailable("Turnstile 검증 응답을 읽지 못했다 — 상태 " + status);
		}
		JsonNode success = answer.path("success");
		if (!success.isBoolean()) {
			return new HumanCheckResult.Unavailable("Turnstile 검증 응답에 success 가 없다 — 상태 " + status);
		}
		if (!success.booleanValue()) {
			return new HumanCheckResult.Rejected(String.join(",", errorCodes(answer)));
		}
		JsonNode testingKey = answer.path("metadata").path("result_with_testing_key");
		if (testingKey.isBoolean() && testingKey.booleanValue()) {
			return new HumanCheckResult.Passed();
		}
		return expected.actionName().equals(answer.path("action").asString(null)) ? new HumanCheckResult.Passed()
				: new HumanCheckResult.Rejected(ACTION_MISMATCH);
	}

	/** 거부가 운영 설정(비밀 키) 오류 때문인가 — 숨기지 않고 크게 알린다 */
	public static boolean secretRejected(HumanCheckResult result) {
		return result instanceof HumanCheckResult.Rejected rejected
				&& List.of(rejected.reason().split(",")).stream().anyMatch(SECRET_ERRORS::contains);
	}

	private static List<String> errorCodes(JsonNode answer) {
		List<String> codes = new ArrayList<>();
		for (JsonNode code : answer.path("error-codes")) {
			if (code.isString()) {
				codes.add(code.stringValue());
			}
		}
		return codes.isEmpty() ? List.of("no-error-code") : codes;
	}

}
