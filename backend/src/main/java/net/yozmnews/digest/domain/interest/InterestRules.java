package net.yozmnews.digest.domain.interest;

import java.time.Duration;

/**
 * 관심도 산식과 그 쓰임(관심 있음 · 구독 제안)의 상수 — 정본. 모두 측정 전 목표다(docs/open-questions.md "운영하면서 결정" 24번) —
 * docs/specs/digest.md §5
 */
public final class InterestRules {

	/** 감쇠의 반감기 — 이만큼 지난 신호 · 노출은 무게가 반이다 */
	public static final Duration HALF_LIFE = Duration.ofDays(30);

	/** 읽는 범위를 반감기 몇 개로 둘지 — 4 면 무게 1/16 미만인 신호 · 노출은 읽지 않는다 */
	public static final int LOOKBACK_HALF_LIVES = 4;

	/** 관심(−1 ~ 1)을 가중치로 옮길 때의 세기(β) — 가중치는 1 − β ~ 1 + β 에 든다 */
	public static final double STRENGTH = 0.5;

	/** 구독 변경 하나의 무게(γ) — 좋아요 하나와 같은 크기 */
	public static final double SUBSCRIPTION_SIGNAL = 1.0;

	/** 분모에 더하는 가상의 노출 수(K) — 노출이 적은 주제를 중립 쪽으로 당긴다 */
	public static final double PRIOR_EXPOSURES = 3.0;

	/** 중립 가중치 — 신호가 없는 주제 */
	public static final double NEUTRAL = 1.0;

	/** "관심 있음"의 관심 x 하한(θ) — 관심사 히스토리의 새로 생긴 관심 · 사라진 관심과 화면에 드러낼 주제를 정한다 */
	public static final double PRESENCE_THRESHOLD = 0.2;

	/** 구독 제안의 관심 x 하한 — 구독하지 않은 중분류의 관심이 이 값 이상이면 제안한다. {@link #PRESENCE_THRESHOLD} 와 쓰임이 다르다 */
	public static final double SUGGESTION_THRESHOLD = 0.35;

	/** 거절한 구독 제안을 다시 내지 않는 기간 — 지난 뒤에도 지금 관심이 거절 때 관심보다 커야 다시 낸다 */
	public static final Duration SUGGESTION_DECLINE_PERIOD = Duration.ofDays(90);

	private InterestRules() {
	}

	/** 읽는 범위의 길이 — {@link #HALF_LIFE} × {@link #LOOKBACK_HALF_LIVES} */
	public static Duration lookback() {
		return HALF_LIFE.multipliedBy(LOOKBACK_HALF_LIVES);
	}

}
