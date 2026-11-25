package net.yozmnews.reader.adapter.in.web;

import net.yozmnews.report.ReportRegenerationOutcome;
import net.yozmnews.report.ReportStatus;
import org.jspecify.annotations.Nullable;

/**
 * 리포트 다시 만들기의 결과 — 판정 결과 200. docs/specs/reader.md §5 · reader-R16
 *
 * @param result 받음 · 실패가 아님 · 횟수 소진 · 쓸 모델 없음
 * @param status 실패가 아닐 때 그 리포트의 지금 상태 — 그 밖에는 null
 */
record ReportRegenerationResponse(ReportRegenerationResult result, @Nullable ReportStatus status) {

	static ReportRegenerationResponse of(ReportRegenerationOutcome outcome) {
		return switch (outcome) {
			case ReportRegenerationOutcome.Requested requested ->
				new ReportRegenerationResponse(ReportRegenerationResult.REQUESTED, null);
			case ReportRegenerationOutcome.NotFailed notFailed ->
				new ReportRegenerationResponse(ReportRegenerationResult.NOT_FAILED, notFailed.status());
			case ReportRegenerationOutcome.LimitReached limit ->
				new ReportRegenerationResponse(ReportRegenerationResult.LIMIT_REACHED, null);
			case ReportRegenerationOutcome.NoUsableModel none ->
				new ReportRegenerationResponse(ReportRegenerationResult.NO_USABLE_MODEL, null);
		};
	}

	/** 다시 만들기의 판별자 */
	enum ReportRegenerationResult {

		/** 받았다 — 리포트가 만드는 중이 된다 */
		REQUESTED,

		/** 실패한 리포트가 아니다 */
		NOT_FAILED,

		/** 그 리포트를 다시 만들 수 있는 횟수를 다 썼다 */
		LIMIT_REACHED,

		/** 쓸 모델이 없다 */
		NO_USABLE_MODEL

	}

}
