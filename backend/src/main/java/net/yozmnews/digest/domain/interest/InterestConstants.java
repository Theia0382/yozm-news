package net.yozmnews.digest.domain.interest;

import java.time.Duration;
import java.util.Objects;

/**
 * 관심 산식과 가중치 변환의 상수 · 성질 묶음 — 키 종류마다 하나. 값의 정본은 {@link InterestRules} 의 이름 있는 묶음이다. 산식은
 * {@link InterestFormula}, 가중치 변환은 g = 1 + β·x — docs/specs/digest.md §5
 *
 * @param halfLife 감쇠의 반감기 — 이만큼 지난 신호 · 노출은 무게가 반이다
 * @param priorExposures 분모에 더하는 가상의 노출 수(K) — 노출이 적은 키를 중립 쪽으로 당긴다
 * @param subscriptionSignal 구독 변경 하나가 관심 x 에 더하는 값(γ) — 노출 분모 밖의 항이다
 * @param strength 관심(−1 ~ 1)을 가중치로 옮길 때의 세기(β) — 가중치는 1 − β ~ 1 + β 에 든다
 * @param reactionsReplaceEarlierSilence 반응이 그 앞의 무반응 노출을 대신하는가 — 켜면 키마다 그 키에 반응(좋아요 · 싫어요)한 노출 가운데
 *     가장 늦은 노출 시각보다 엄격히 앞선 무반응 노출을 그 키의 노출에서 뺀다(docs/specs/digest.md digest-R23). 같은 이야기를 거듭 보이는
 *     흐름에서 앞선 침묵이 반응을 묽히지 않게 한다
 */
public record InterestConstants(Duration halfLife, double priorExposures, double subscriptionSignal, double strength,
		boolean reactionsReplaceEarlierSilence) {

	/**
	 * @throws IllegalArgumentException 반감기가 0 이하이거나, K · γ 가 0 이하이거나, β 가 0 이하 또는 1 이상일 때
	 */
	public InterestConstants {
		Objects.requireNonNull(halfLife, "halfLife");
		if (halfLife.isNegative() || halfLife.isZero()) {
			throw new IllegalArgumentException("반감기는 0 보다 커야 한다: " + halfLife);
		}
		if (!(priorExposures > 0.0)) {
			throw new IllegalArgumentException("가상의 노출 수는 0 보다 커야 한다: " + priorExposures);
		}
		if (!(subscriptionSignal > 0.0)) {
			throw new IllegalArgumentException("구독 변경이 더하는 값은 0 보다 커야 한다: " + subscriptionSignal);
		}
		if (!(strength > 0.0 && strength < 1.0)) {
			throw new IllegalArgumentException("가중치의 세기는 0 보다 크고 1 보다 작아야 한다: " + strength);
		}
	}

}
