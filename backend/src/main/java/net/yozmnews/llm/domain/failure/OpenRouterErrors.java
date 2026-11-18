package net.yozmnews.llm.domain.failure;

import java.util.Objects;

import net.yozmnews.llm.LlmFailureKind;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenRouter 오류를 실패 종류로 가르는 정본 — docs/specs/llm.md §4 · llm-R8.
 * <p>
 * 상태 코드와 오류의 종류 이름({@code error.metadata.error_type})을 읽는다. 403 에는 키 권한 · 가드레일 문제와 입력 내용 차단(모델 거절 · 내용
 * 필터 · 검열)이 섞여 있어, 권한 표시({@code permission_denied})가 있을 때만 {@link LlmFailureKind#PERMISSION_DENIED} 다 — 표시가 없거나
 * 다른 값이면 키 상태를 바꾸지 않는 {@link LlmFailureKind#UNEXPECTED} 다. 오류 본문의 {@code message} 와 {@code metadata} 의 다른 칸(검열에
 * 걸린 입력 조각 등)은 읽지 않는다(llm-R9).
 */
public final class OpenRouterErrors {

	/** 키 권한 · 가드레일이 막은 403 의 종류 이름 */
	private static final String PERMISSION_DENIED = "permission_denied";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private OpenRouterErrors() {
	}

	public static LlmFailureKind classify(ProviderError error) {
		Objects.requireNonNull(error, "error");
		return switch (error.status()) {
			case 401 -> LlmFailureKind.INVALID_KEY;
			case 402 -> LlmFailureKind.QUOTA_EXHAUSTED;
			case 403 -> PERMISSION_DENIED.equals(error.type()) ? LlmFailureKind.PERMISSION_DENIED
					: LlmFailureKind.UNEXPECTED;
			case 429 -> LlmFailureKind.TRANSIENT;
			default -> error.retriableStatus() ? LlmFailureKind.TRANSIENT : LlmFailureKind.UNEXPECTED;
		};
	}

	/**
	 * 오류 본문에서 종류 이름({@code metadata.error_type})을 읽는다 — 최상위 {@code error} 마디가 있으면 그 안에서, 없으면 최상위에서 읽는다.
	 *
	 * @return 종류 이름 — 없거나 문자열이 아니거나 본문이 JSON 객체가 아니면 null
	 */
	public static @Nullable String errorType(String body) {
		Objects.requireNonNull(body, "body");
		JsonNode root;
		try {
			root = JSON.readTree(body);
		}
		catch (JacksonException ex) {
			return null;
		}
		if (root == null || !root.isObject()) {
			return null;
		}
		JsonNode error = root.get("error");
		JsonNode holder = (error != null && error.isObject()) ? error : root;
		JsonNode metadata = holder.get("metadata");
		if (metadata == null || !metadata.isObject()) {
			return null;
		}
		JsonNode type = metadata.get("error_type");
		return (type != null && type.isString()) ? type.asString() : null;
	}

}
