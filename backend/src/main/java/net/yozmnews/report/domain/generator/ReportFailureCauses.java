package net.yozmnews.report.domain.generator;

import java.util.Objects;

import net.yozmnews.report.ReportFailure;
import net.yozmnews.user.GenerationFailureCause;

/**
 * 리포트 실패 코드를 사용자가 할 일의 갈래로 옮기는 정본 — 실패 알림이 싣는다. docs/specs/report.md §5
 * <p>
 * 망라 분기라 실패 코드가 늘면 컴파일 오류가 난다.
 */
public final class ReportFailureCauses {

	private ReportFailureCauses() {
	}

	/** 실패 코드의 갈래 */
	public static GenerationFailureCause of(ReportFailure failure) {
		Objects.requireNonNull(failure, "failure");
		return switch (failure) {
			case KEY_MISSING, KEY_INVALID, NO_USABLE_MODEL -> GenerationFailureCause.KEY;
			case PERMISSION_DENIED, QUOTA_EXHAUSTED -> GenerationFailureCause.PROVIDER_ACCOUNT;
			case PROVIDER_UNAVAILABLE, PROVIDER_UNEXPECTED, INTERRUPTED -> GenerationFailureCause.TEMPORARY;
			case UNREADABLE_OUTPUT, OUTPUT_TRUNCATED, OUTPUT_REFUSED -> GenerationFailureCause.OUTPUT;
		};
	}

}
