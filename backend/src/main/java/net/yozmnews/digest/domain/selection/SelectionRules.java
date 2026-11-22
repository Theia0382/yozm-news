package net.yozmnews.digest.domain.selection;

import java.time.Duration;

/**
 * 후보 선별의 상수 — 정본. 모두 측정 전 목표다(docs/open-questions.md "운영하면서 결정" 9 · 21번) — docs/specs/digest.md §5
 */
public final class SelectionRules {

	/** {@code low_confidence} 사건(평가 있는 소속 기사가 모두 {@code low_confidence})의 중요도에 곱하는 가중치 — 중요도 4 인 사건은 2.8 로 센다 */
	public static final double LOW_CONFIDENCE_WEIGHT = 0.7;

	/**
	 * 발행 시각의 하한 — 첫 평가 시각이 기간 안이어도 발행 시각이 기간 시작에서 이만큼보다 앞서면 구독 후보 사건의 기사로 넣지 않는다. 소스를 새로 켰을 때 피드에 남아
	 * 있던 오래된 기사가 한꺼번에 들어오지 않게 한다
	 */
	public static final Duration PUBLISHED_LOOKBACK = Duration.ofHours(24);

	/** 큰 스토리라인 하나에서 넣는 사건 수의 상한 */
	public static final int BIG_EVENTS_PER_STORYLINE = 10;

	/**
	 * 큰 슬롯 사건이 차지할 수 있는 사건 수의 몫 — 고를 때는 최대 후보 수 × 이 값을 내림한 수까지, 입력 토큰 예산으로 자를 때는 남는 사건 수 × 이
	 * 값을 내림한 수까지(구독 사건이 하나라도 빠졌을 때 — 구독 사건이 모자라면 큰 슬롯이 남는 예산을 쓴다, {@link BudgetCut}). 관심 설정
	 * 화면의 안내 문구("최대 후보 수의 절반" — {@code frontend/src/features/interests/interestTexts.ts} 의
	 * {@code BIG_SLOTS_HELP_TEXT})가 이 값을 말로 적는다 — 값을 바꾸면 그 문구도 고친다
	 */
	public static final double BIG_SHARE = 0.5;

	/**
	 * 주제 구독 밖 관심 흐름의 길이 차지할 수 있는 몫 — 구독 사건의 자리(최대 후보 수 − 큰 슬롯 사건 수) × 이 값을 내림한 수까지
	 * ({@link FollowedStorylines}). 흐름마다 한 사건이다
	 */
	public static final double FOLLOWED_SHARE = 0.2;

	private SelectionRules() {
	}

	/**
	 * 구독 사건의 자리에서 주제 구독 밖 관심 흐름의 길이 먼저 쓸 수 있는 수 — {@link #FOLLOWED_SHARE} 를 곱해 내림
	 * @param seats 구독 사건의 자리
	 */
	public static int followedSeats(int seats) {
		if (seats < 0) {
			throw new IllegalArgumentException("자리 수가 음수다: " + seats);
		}
		return (int) Math.floor(seats * FOLLOWED_SHARE);
	}

	/**
	 * 사건 수에서 큰 슬롯 사건이 차지할 수 있는 수 — {@link #BIG_SHARE} 를 곱해 내림. 고르기는 최대 후보 수로({@link BigSlotPicker}), 예산
	 * 자르기는 남는 사건 수로({@link BudgetCut}) 부른다
	 * @param events 사건 수
	 */
	public static int bigSeats(int events) {
		if (events < 0) {
			throw new IllegalArgumentException("사건 수가 음수다: " + events);
		}
		return (int) Math.floor(events * BIG_SHARE);
	}

}
