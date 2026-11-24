package net.yozmnews.report.domain.prompt;

import java.util.Objects;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.report.ReportPeriodKind;

/**
 * 리포트 입력의 예산 — 리포트 상한 · 지시 몫 · 직전 글의 몫과 사용자 구획 예산 산식의 정본 · docs/specs/report.md §5 · report-R22
 * <p>
 * 실제 상한은 쓸 모델의 입력 토큰 예산(바깥 안전 상한)과 리포트 종류의 상한 가운데 작은 값이다. 사용자 구획 예산은 그 상한에서 지시 몫을 뺀 값이다.
 * 값은 모두 측정 전 목표다.
 */
public final class ReportInputBudget {

	/** 주간 리포트의 상한(토큰) */
	public static final int WEEK_CAP = 80_000;

	/** 월간 리포트의 상한(토큰) */
	public static final int MONTH_CAP = 160_000;

	/** 지시 · 출력 스키마 · 다시 묻는 문장과 진단의 몫(토큰) — 실제 상한에서 먼저 뺀다 */
	public static final int INSTRUCTION_RESERVE = 8_000;

	/** 사용자 구획 예산 가운데 직전 리포트 글이 쓸 수 있는 비율 */
	public static final double PRIOR_SHARE = 0.3;

	private ReportInputBudget() {
	}

	/** 리포트 종류의 상한(토큰) */
	public static int cap(ReportPeriodKind kind) {
		Objects.requireNonNull(kind, "kind");
		return switch (kind) {
			case WEEK -> WEEK_CAP;
			case MONTH -> MONTH_CAP;
		};
	}

	/**
	 * 사용자 구획 예산 — {@code max(0, min(모델의 입력 토큰 예산, 리포트 상한) − 지시 몫)}
	 */
	public static int userContent(LlmModel model, ReportPeriodKind kind) {
		Objects.requireNonNull(model, "model");
		return Math.max(0, Math.min(model.inputTokenBudget(), cap(kind)) - INSTRUCTION_RESERVE);
	}

	/**
	 * 직전 리포트 글 전체의 몫 — 사용자 구획 예산 × {@link #PRIOR_SHARE} 의 내림
	 * @throws IllegalArgumentException 예산이 음수일 때
	 */
	public static int priorShare(int budget) {
		if (budget < 0) {
			throw new IllegalArgumentException("예산이 음수다: " + budget);
		}
		return (int) Math.floor(budget * PRIOR_SHARE);
	}

}
