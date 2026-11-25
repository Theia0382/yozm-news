package net.yozmnews.report;

import java.util.Objects;

/**
 * 리포트 다시 만들기의 결과 · docs/specs/report.md §3
 */
public sealed interface ReportRegenerationOutcome {

	/** 받았다 — 리포트가 생성을 기다림이 되었고 생성은 작업 서버가 한다 */
	record Requested() implements ReportRegenerationOutcome {
	}

	/**
	 * 실패한 리포트가 아니다 — 생성을 기다리거나 생성 중이거나 끝났지만 실패가 아니다.
	 * @param status 지금 상태
	 */
	record NotFailed(ReportStatus status) implements ReportRegenerationOutcome {

		public NotFailed {
			Objects.requireNonNull(status, "status");
		}

	}

	/** 그 리포트를 다시 만들 수 있는 횟수를 다 썼다 */
	record LimitReached() implements ReportRegenerationOutcome {
	}

	/** 쓸 모델이 없다 — 유효한 키가 있는 공급자의 허용 모델이 없다 */
	record NoUsableModel() implements ReportRegenerationOutcome {
	}

}
