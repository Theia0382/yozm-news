package net.yozmnews.credential.domain.key;

import java.util.Objects;
import java.util.Optional;

import net.yozmnews.llm.KeyValidationResult;

/**
 * 키의 상태 — docs/specs/credential.md §4 · credential-R14
 */
public enum KeyStatus {

	/** 쓸 수 있다 — 등록된 키는 이 상태로 시작하고, 무효였던 키가 성공하면 돌아온다 */
	VALID,

	/** 공급자가 무효 · 권한 없음으로 답했다 */
	INVALID;

	/**
	 * 저장한 키로 공급자에 물은 결과에서 다음 상태를 정한다 — 정본.
	 * @return 다음 상태 — 비어 있으면 상태를 바꾸지 않는다
	 */
	public static Optional<KeyStatus> after(KeyValidationResult result) {
		Objects.requireNonNull(result, "result");
		return switch (result) {
			case KeyValidationResult.Valid valid -> Optional.of(VALID);
			case KeyValidationResult.Rejected rejected -> switch (rejected.kind()) {
				case INVALID_KEY, PERMISSION_DENIED -> Optional.of(INVALID);
				case QUOTA_EXHAUSTED, TRANSIENT, UNEXPECTED -> Optional.empty();
			};
		};
	}

}
