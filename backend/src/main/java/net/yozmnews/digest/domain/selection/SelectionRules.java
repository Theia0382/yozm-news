package net.yozmnews.digest.domain.selection;

import java.time.Duration;

/**
 * 후보 선별의 상수 — 정본. 모두 측정 전 목표다(docs/open-questions.md "운영하면서 결정" 9 · 21번) — docs/specs/digest.md §5
 */
public final class SelectionRules {

	/** {@code low_confidence} 기사의 중요도에 곱하는 가중치 — 중요도 4 인 {@code low_confidence} 기사는 2.8 로 센다 */
	public static final double LOW_CONFIDENCE_WEIGHT = 0.7;

	/**
	 * 발행 시각의 하한 — 첫 평가 시각이 기간 안이어도 발행 시각이 기간 시작에서 이만큼보다 앞서면 넣지 않는다. 소스를 새로 켰을 때 피드에 남아
	 * 있던 오래된 기사가 한꺼번에 들어오지 않게 한다
	 */
	public static final Duration PUBLISHED_LOOKBACK = Duration.ofHours(24);

	/** 큰 스토리라인 하나에서 넣는 사건 수의 상한 — 사건마다 대표 기사 하나 */
	public static final int BIG_EVENTS_PER_STORYLINE = 10;

	/**
	 * 큰 슬롯 기사가 차지할 수 있는 최대 후보 수의 몫 — 최대 후보 수 × 이 값을 내림한 수까지. 관심 설정 화면의 안내 문구("최대 후보 수의
	 * 절반" — {@code frontend/src/features/interests/interestTexts.ts} 의 {@code BIG_SLOTS_HELP_TEXT})가 이 값을 말로 적는다 —
	 * 값을 바꾸면 그 문구도 고친다
	 */
	public static final double BIG_SHARE = 0.5;

	private SelectionRules() {
	}

	/** 최대 후보 수에서 큰 슬롯 기사가 차지할 수 있는 수 — {@link #BIG_SHARE} 를 곱해 내림 */
	public static int bigSeats(int maxCandidates) {
		if (maxCandidates < 0) {
			throw new IllegalArgumentException("최대 후보 수가 음수다: " + maxCandidates);
		}
		return (int) Math.floor(maxCandidates * BIG_SHARE);
	}

}
