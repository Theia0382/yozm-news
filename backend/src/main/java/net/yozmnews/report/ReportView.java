package net.yozmnews.report;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 리포트 한 편 · docs/specs/report.md §3 · report-R11 · report-R12
 *
 * @param provider 시작할 때 적은 공급자 키 — 시작 전이면 {@code null}
 * @param model 시작할 때 적은 모델 ID — 시작 전이면 {@code null}
 * @param inputTokens 공급자가 센 입력 토큰 — 공급자가 답한 생성일 때만
 * @param outputTokens 공급자가 센 출력 토큰 — 공급자가 답한 생성일 때만
 * @param statsSnapshot 생성에 쓴 통계 스냅숏(JSON 객체 문자열) — 시작 전이면 {@code null}
 * @param sections 보이는 섹션 — 위치 순. {@code READY} 가 아니면 빈 목록
 */
public record ReportView(ReportSummary summary, @Nullable String provider, @Nullable String model,
		@Nullable Integer inputTokens, @Nullable Integer outputTokens, @Nullable String statsSnapshot,
		List<ReportSectionView> sections) {

	public ReportView {
		Objects.requireNonNull(summary, "summary");
		sections = List.copyOf(sections);
	}

}
