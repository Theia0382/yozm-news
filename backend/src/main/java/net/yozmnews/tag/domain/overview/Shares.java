package net.yozmnews.tag.domain.overview;

import net.yozmnews.tag.Share;

/**
 * 평가 현황의 비율 — 수 ÷ 평가 수, 평가가 없으면 비율 없음 · 비율 산식의 정본 · docs/specs/tag.md §5
 */
public final class Shares {

	private Shares() {
	}

	/**
	 * @param evaluations 분모 — 그 기간 · 버전의 평가 수
	 */
	public static Share of(long count, long evaluations) {
		return new Share(count, (evaluations == 0) ? null : (double) count / evaluations);
	}

}
