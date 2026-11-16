package net.yozmnews.storyline.domain.linking;

import org.jspecify.annotations.Nullable;

/**
 * 띠 안 후보 하나에 물은 결과 — 흐름 소속 판정의 확률과 판정 원본 ID · docs/specs/storyline.md §4 · storyline-R35.
 * <p>
 * 둘 다 없으면 확률을 얻지 못한 것이다(입력 거부 · 서술이 모두 빠져 묻지 못함) — 그 후보는 다시 물을 때까지 붙일 수 없다.
 *
 * @param probability 같은 사안의 전개일 확률 — 0 ~ 1
 * @param judgementId 판정 원본 ID — {@code evaluation} 의 흐름 소속 판정 원본
 */
public record Asked(@Nullable Double probability, @Nullable Long judgementId) {

	/** 확률을 얻지 못했다 */
	public static final Asked UNANSWERED = new Asked(null, null);

	/**
	 * @throws IllegalArgumentException 확률과 원본 ID 가 함께 있거나 함께 없지 않을 때, 확률이 0 ~ 1 의 유한값이 아닐 때, 원본 ID 가 1
	 * 미만일 때
	 */
	public Asked {
		if ((probability == null) != (judgementId == null)) {
			throw new IllegalArgumentException("확률과 판정 원본 ID 는 함께 있거나 함께 없다: " + probability + " · " + judgementId);
		}
		if (probability != null && !(Double.isFinite(probability) && probability >= 0 && probability <= 1)) {
			throw new IllegalArgumentException("확률은 0 ~ 1 의 유한값이다: " + probability);
		}
		if (judgementId != null && judgementId < 1) {
			throw new IllegalArgumentException("판정 원본 ID 는 1 이상이다: " + judgementId);
		}
	}

	/** 확률을 얻었는가 */
	public boolean answered() {
		return this.probability != null;
	}

	/** 확률 기준 이상인가 — 확률이 없으면 아니다 */
	public boolean reaches(double threshold) {
		return this.probability != null && this.probability >= threshold;
	}

}
