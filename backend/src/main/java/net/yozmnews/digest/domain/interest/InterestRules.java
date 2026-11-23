package net.yozmnews.digest.domain.interest;

import java.time.Duration;
import java.time.Period;

/**
 * 관심도 산식과 그 쓰임(관심 있음 · 구독 제안)의 상수 — 정본. 모두 측정 전 목표다(docs/open-questions.md "운영하면서 결정" 24번) —
 * docs/specs/digest.md §5
 */
public final class InterestRules {

	/**
	 * 주제 중분류 관심의 상수 — 반감기 30일 · K 3(노출이 적은 주제를 중립 쪽으로) · γ 0.1(구독 변경 하나가 관심에 더하는 값 — "관심 있음" 기준
	 * {@link #PRESENCE_THRESHOLD} 보다 작아 구독만으로는 관심 있음이 되지 않는다) · β 0.5(가중치는 0.5 ~ 1.5)
	 */
	public static final InterestConstants TOPIC = new InterestConstants(Duration.ofDays(30), 3.0, 0.1, 0.5);

	/**
	 * 스토리라인(포함 나무의 꼭대기) 관심의 상수 — 반감기 30일 · K 3 · γ 1/3(추적만 한 흐름의 관심 — 노출이 쌓여도 그대로) · β 0.5. 주제와
	 * 따로 바꿀 수 있게 따로 둔다
	 */
	public static final InterestConstants STORYLINE = new InterestConstants(Duration.ofDays(30), 3.0, 1.0 / 3.0, 0.5);

	/**
	 * 관심의 거름 기준 — 흐름 관심과 큰 슬롯 사건의 주제 관심이 같은 값을 쓴다. 스토리라인 관심 x 가 이 값 이하(경계 포함)인 꼭대기는 거른다
	 * ({@link StorylineLevels#excluded}): 그 꼭대기만 가진 사건은 후보에서 빠지고, 머리가 그 꼭대기인 큰 판정 행은 건너뛴다. 주제 관심의
	 * 평균이 이 값 이하(경계 포함)인 사건은 큰 슬롯에 넣지 않는다({@link InterestLevels#excluded}). 처음 값은 혼자인 항목의 사건 하나에 싫어요
	 * 한 번으로 닿는 높이다(주제 · 흐름 모두 K 3 이라 −0.25)
	 */
	public static final double EXCLUSION_THRESHOLD = -0.2;

	/**
	 * 관심 흐름의 기준 — 관심 x 가 이 값 이상인 꼭대기는 (추적 중인 사용자 스토리라인의 꼭대기와 함께) 관심 흐름이다
	 * ({@link StorylineLevels#followed}). 주제 구독 밖 관심 흐름의 길도 같은 정의를 쓴다
	 */
	public static final double STORYLINE_FOLLOW_THRESHOLD = 0.35;

	/** 남길 흐름의 기준 — 한 번 보인 흐름은 관심 x 가 이 값 이상이면 남는다({@link StorylineLevels#kept}) */
	public static final double STORYLINE_KEEP_THRESHOLD = 0.2;

	/**
	 * 반복의 기간 — 사건의 첫 노출 날짜 앞 이 기간 안(앞선 날짜)에 그 사건의 꼭대기가 노출됐으면 그 사건은 다시 본 흐름(반복)이고, 그 노출 ·
	 * 반응은 스토리라인 관심에만 든다
	 */
	public static final Period STORYLINE_REPEAT_WINDOW = Period.ofDays(7);

	/** 읽는 범위를 반감기 몇 개로 둘지 — 4 면 무게 1/16 미만인 신호 · 노출은 읽지 않는다 */
	public static final int LOOKBACK_HALF_LIVES = 4;

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

	/** 읽는 범위의 길이 — {@link #TOPIC} · {@link #STORYLINE} 반감기의 큰 쪽 × {@link #LOOKBACK_HALF_LIVES} */
	public static Duration lookback() {
		Duration longer = (TOPIC.halfLife().compareTo(STORYLINE.halfLife()) >= 0) ? TOPIC.halfLife()
				: STORYLINE.halfLife();
		return longer.multipliedBy(LOOKBACK_HALF_LIVES);
	}

}
