package net.yozmnews.report;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 리포트 한 편 · docs/specs/report.md §3 · report-R11 · report-R12 · report-R25
 *
 * @param provider 시작할 때 적은 공급자 키 — 시작 전이면 {@code null}
 * @param model 시작할 때 적은 모델 ID — 시작 전이면 {@code null}
 * @param inputTokens 공급자가 센 입력 토큰 — 공급자가 답한 생성일 때만
 * @param outputTokens 공급자가 센 출력 토큰 — 공급자가 답한 생성일 때만
 * @param previous 생성 때 이은 직전 리포트(report-R18)의 요약 — 이은 리포트가 없었거나 그새 없어졌으면 {@code null}
 * @param hiddenSections 인용을 모두 잃어 숨긴 갈래 섹션 수 — {@code READY} 가 아니면 0
 * @param sections 보이는 섹션 — 위치 순. {@code READY} 가 아니면 빈 목록
 */
public record ReportView(ReportSummary summary, @Nullable String provider, @Nullable String model,
		@Nullable Integer inputTokens, @Nullable Integer outputTokens, @Nullable ReportSummary previous,
		int hiddenSections, List<ReportSectionView> sections) {

	/**
	 * @throws IllegalArgumentException 숨긴 섹션 수가 0 보다 작을 때
	 */
	public ReportView {
		Objects.requireNonNull(summary, "summary");
		if (hiddenSections < 0) {
			throw new IllegalArgumentException("숨긴 섹션 수는 0 이상이다: " + hiddenSections);
		}
		sections = List.copyOf(sections);
	}

}
