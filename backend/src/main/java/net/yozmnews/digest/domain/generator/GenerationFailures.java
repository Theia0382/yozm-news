package net.yozmnews.digest.domain.generator;

import java.util.Objects;

import net.yozmnews.digest.domain.digest.DigestFailure;
import net.yozmnews.llm.LlmFailureKind;

/**
 * 관문이 가른 실패 종류를 다이제스트 실패 코드로 옮기기 — 정본 — docs/specs/digest.md §5
 * <p>
 * 종류마다 코드 하나다 — 사용자에게 보일 안내(키 다시 등록 · 결제 · 나중에)가 종류마다 다르다.
 */
public final class GenerationFailures {

	private GenerationFailures() {
	}

	public static DigestFailure of(LlmFailureKind kind) {
		Objects.requireNonNull(kind, "kind");
		return switch (kind) {
			case INVALID_KEY -> DigestFailure.KEY_INVALID;
			case PERMISSION_DENIED -> DigestFailure.PERMISSION_DENIED;
			case QUOTA_EXHAUSTED -> DigestFailure.QUOTA_EXHAUSTED;
			case TRANSIENT -> DigestFailure.PROVIDER_UNAVAILABLE;
			case UNEXPECTED -> DigestFailure.PROVIDER_UNEXPECTED;
		};
	}

}
