package net.yozmnews.digest.domain.digest;

import net.yozmnews.digest.DigestFailure;
import net.yozmnews.user.DigestFailureCause;

/**
 * 실패 코드를 실패 알림의 갈래로 옮기는 정본 — 사용자가 할 일로 묶는다. docs/specs/digest.md §5
 */
public final class FailureCauses {

	private FailureCauses() {
	}

	/** 실패 코드의 갈래 */
	public static DigestFailureCause of(DigestFailure failure) {
		return switch (failure) {
			case KEY_MISSING, KEY_INVALID, NO_USABLE_MODEL -> DigestFailureCause.KEY;
			case PERMISSION_DENIED, QUOTA_EXHAUSTED -> DigestFailureCause.PROVIDER_ACCOUNT;
			case PROVIDER_UNAVAILABLE, PROVIDER_UNEXPECTED, INTERRUPTED -> DigestFailureCause.TEMPORARY;
			case UNREADABLE_OUTPUT, NO_CITATION, OUTPUT_TRUNCATED, OUTPUT_REFUSED -> DigestFailureCause.OUTPUT;
		};
	}

}
