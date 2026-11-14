package net.yozmnews.user.domain.session;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

/**
 * 세션 체인의 인가 판정 — 정본 · docs/specs/user.md §4 · user-R5
 */
public enum SessionAccess {

	/** 연다 */
	OPEN,

	/** 세션 사용자가 없다 */
	UNAUTHENTICATED,

	/** 세션 사용자가 비밀번호를 바꿔야 한다 */
	PASSWORD_CHANGE_REQUIRED,

	/** 세션 사용자에게 LLM 키가 없다 */
	LLM_KEY_REQUIRED;

	/**
	 * 처리기의 표시와 세션 사용자의 상태로 판정한다 — 공개 · 처리기 없음, 세션 사용자, 강제 변경, 키의 순서다.
	 * @param mustChangePassword 세션 사용자가 비밀번호를 바꿔야 하는가 — 세션 사용자가 없으면 null
	 * @param hasLlmKey 세션 사용자에게 LLM 키가 있는가 — 앞 갈래에서 끝나지 않은 요청에만 묻는다
	 */
	public static SessionAccess decide(HandlerMarks marks, @Nullable Boolean mustChangePassword,
			BooleanSupplier hasLlmKey) {
		Objects.requireNonNull(marks, "marks");
		Objects.requireNonNull(hasLlmKey, "hasLlmKey");
		if (!marks.present() || marks.isPublic()) {
			return OPEN;
		}
		if (mustChangePassword == null) {
			return UNAUTHENTICATED;
		}
		if (mustChangePassword) {
			return marks.beforePasswordChange() ? OPEN : PASSWORD_CHANGE_REQUIRED;
		}
		if (marks.withoutLlmKey() || hasLlmKey.getAsBoolean()) {
			return OPEN;
		}
		return LLM_KEY_REQUIRED;
	}

}
