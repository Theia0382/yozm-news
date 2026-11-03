package net.yozmnews.evaluation;

import org.jspecify.annotations.Nullable;

/**
 * 평가 현황의 수와 비율 — docs/specs/evaluation.md §3
 *
 * @param count 수 — 음수가 아니다
 * @param ratio 수 ÷ 그 기간 · 버전의 평가 수 — 평가가 없으면 null
 */
public record Share(long count, @Nullable Double ratio) {

	/**
	 * @throws IllegalArgumentException 수가 음수일 때
	 */
	public Share {
		if (count < 0) {
			throw new IllegalArgumentException("수는 음수가 아니다: " + count);
		}
	}

}
