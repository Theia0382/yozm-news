package net.yozmnews.reader.adapter.in.web;

import net.yozmnews.report.ReportView;
import org.jspecify.annotations.Nullable;

/**
 * 변화 리포트의 사용량 — 시작할 때 적은 공급자 · 모델과 공급자가 센 토큰. 금액으로 바꾸지 않는다. docs/specs/reader.md §5 reader-R16.
 *
 * @param inputTokens 입력 토큰 — 공급자가 답한 생성일 때만
 * @param outputTokens 출력 토큰 — 공급자가 답한 생성일 때만
 */
record ReportUsageResponse(String provider, String model, @Nullable Integer inputTokens,
		@Nullable Integer outputTokens) {

	/** 시작 전(공급자가 없다)이면 null */
	static @Nullable ReportUsageResponse of(ReportView view) {
		if (view.provider() == null || view.model() == null) {
			return null;
		}
		return new ReportUsageResponse(view.provider(), view.model(), view.inputTokens(), view.outputTokens());
	}

}
