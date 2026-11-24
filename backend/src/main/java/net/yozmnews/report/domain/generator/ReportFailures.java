package net.yozmnews.report.domain.generator;

import java.util.Objects;

import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.report.ReportFailure;

/**
 * 관문이 가른 실패 종류를 리포트 실패 코드로 옮기기 — 정본 · docs/specs/report.md §5 · report-R24
 * <p>
 * 종류마다 코드 하나다 — 다이제스트의 코드와 같은 이름이다.
 */
public final class ReportFailures {

	private ReportFailures() {
	}

	public static ReportFailure of(LlmFailureKind kind) {
		Objects.requireNonNull(kind, "kind");
		return switch (kind) {
			case INVALID_KEY -> ReportFailure.KEY_INVALID;
			case PERMISSION_DENIED -> ReportFailure.PERMISSION_DENIED;
			case QUOTA_EXHAUSTED -> ReportFailure.QUOTA_EXHAUSTED;
			case TRANSIENT -> ReportFailure.PROVIDER_UNAVAILABLE;
			case UNEXPECTED -> ReportFailure.PROVIDER_UNEXPECTED;
		};
	}

}
