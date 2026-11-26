package net.yozmnews.report.domain.prompt;

import java.util.Objects;

import net.yozmnews.report.domain.input.ReportInput;

/**
 * 입력 맞추기의 결과 · docs/specs/report.md §5 · report-R22
 *
 * @param input 맞춘 입력 — 사용자 · 기간 · 시간대 · 스냅숏은 맞추기 전과 같다
 * @param trimmed 단계마다 덜어 낸 수 — 로그에만 쓴다
 * @param overBudget 마지막 단계 뒤에도 예산을 넘긴 채인가
 */
public record FittedInput(ReportInput input, Trimmed trimmed, boolean overBudget) {

	public FittedInput {
		Objects.requireNonNull(input, "input");
		Objects.requireNonNull(trimmed, "trimmed");
	}

	/** 덜어 낸 것이 있는가 */
	public boolean trimmedAnything() {
		return !this.trimmed.equals(Trimmed.NONE);
	}

	/**
	 * 단계마다 덜어 낸 수
	 *
	 * @param priorsCut 1 단계 — 본문을 자른 직전 리포트 글의 편 수
	 * @param userStorylineSummariesDropped 2 · 5 단계 — 사용자 스토리라인 관심 줄에서 뺀 정리의 수
	 * @param eventLinesDropped 3 단계 — 뺀 사건 줄의 수
	 * @param userStorylineOverviewsDropped 4 단계 — 흐름 요약을 비운 사용자 스토리라인의 수
	 * @param userStorylinesDropped 5 단계 — 뺀 추적한 사용자 스토리라인의 수
	 */
	public record Trimmed(int priorsCut, int userStorylineSummariesDropped, int eventLinesDropped, int userStorylineOverviewsDropped,
			int userStorylinesDropped) {

		/** 덜어 낸 것이 없다 */
		public static final Trimmed NONE = new Trimmed(0, 0, 0, 0, 0);

		/**
		 * @throws IllegalArgumentException 수가 음수일 때
		 */
		public Trimmed {
			if (priorsCut < 0 || userStorylineSummariesDropped < 0 || eventLinesDropped < 0 || userStorylineOverviewsDropped < 0
					|| userStorylinesDropped < 0) {
				throw new IllegalArgumentException("덜어 낸 수가 음수다");
			}
		}

	}

}
