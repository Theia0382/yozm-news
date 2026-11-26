package net.yozmnews.platform;

import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 회차 하나의 최근 기록 — docs/specs/platform.md §3 · 반복 회차.
 *
 * @param handled 최근 창 안에 끝난 회차들이 처리한 수의 합
 * @param runs 최근 창 안에 끝난 회차 수
 * @param last 마지막으로 끝난 회차 — 창 밖이어도 보존 기간 안이면 낸다. 기록이 없으면 null
 */
public record RoundSummary(long handled, int runs, @Nullable LastRound last) {

	/** 기록이 없다 */
	public static final RoundSummary NONE = new RoundSummary(0, 0, null);

	/**
	 * @throws IllegalArgumentException 합이나 회차 수가 음수일 때
	 */
	public RoundSummary {
		if (handled < 0 || runs < 0) {
			throw new IllegalArgumentException("합과 회차 수는 음수가 아니다");
		}
	}

	/**
	 * 끝난 회차 하나.
	 *
	 * @param startedAt 시작 시각
	 * @param endedAt 끝난 시각
	 * @param batches 부른 뭉치 수
	 * @param handled 처리한 수
	 * @param end 끝난 까닭
	 */
	public record LastRound(Instant startedAt, Instant endedAt, int batches, int handled, RoundEnd end) {

		public LastRound {
			Objects.requireNonNull(startedAt, "startedAt");
			Objects.requireNonNull(endedAt, "endedAt");
			Objects.requireNonNull(end, "end");
		}

	}

}
