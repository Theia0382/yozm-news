package net.yozmnews.storyline.domain.linking;

/**
 * 점수와 그 재료 — 소속의 판정 근거로 남는다 · docs/specs/storyline.md §4 · storyline-R8 · storyline-R14.
 *
 * @param score 점수 — 0 ~ 1, 코사인을 셀 수 없으면 NaN
 * @param cosine 중심의 코사인 유사도
 * @param overlap 핵심 말 겹침 — 0 ~ 1
 */
public record Score(double score, double cosine, double overlap) {

	/** 기준 이상인가 — NaN 은 기준에 닿지 않는다 */
	public boolean reaches(double threshold) {
		return this.score >= threshold;
	}

}
