package net.yozmnews.storyline.domain.storyline;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import net.yozmnews.storyline.StorylineState;

/**
 * 상태 판정의 정본 — 기본값의 정본은 설정({@code StorylineStateProperties})이고, 이 타입은 검증과 판정을 맡는다 ·
 * docs/specs/storyline.md §4 · storyline-R16.
 *
 * @param quietAfter 잠잠 기간 — 마지막 기사 뒤 이만큼 지나면 잠잠이다
 * @param endedAfter 끝남 기간 — 마지막 기사 뒤 이만큼 지나면 끝남이다
 */
public record StateCriteria(Duration quietAfter, Duration endedAfter) {

	/**
	 * @throws IllegalArgumentException 기간이 0 이하이거나 초 단위가 아니거나, 잠잠 기간이 끝남 기간보다 짧지 않을 때
	 */
	public StateCriteria {
		Objects.requireNonNull(quietAfter, "quietAfter");
		Objects.requireNonNull(endedAfter, "endedAfter");
		requireWholeSeconds("잠잠 기간", quietAfter);
		requireWholeSeconds("끝남 기간", endedAfter);
		if (quietAfter.compareTo(endedAfter) >= 0) {
			throw new IllegalArgumentException("잠잠 기간은 끝남 기간보다 짧아야 한다: " + quietAfter + " · " + endedAfter);
		}
	}

	/**
	 * 지금 − 마지막 기사 시각이 끝남 기간 이상이면 끝남, 잠잠 기간 이상이면 잠잠, 그 밖(마지막 기사 시각이 지금보다 뒤인 경우 포함)은 진행이다 —
	 * 경계 포함.
	 */
	public StorylineState stateOf(Instant lastArticleAt, Instant now) {
		Duration since = Duration.between(lastArticleAt, now);
		if (since.compareTo(this.endedAfter) >= 0) {
			return StorylineState.ENDED;
		}
		if (since.compareTo(this.quietAfter) >= 0) {
			return StorylineState.QUIET;
		}
		return StorylineState.ACTIVE;
	}

	private static void requireWholeSeconds(String name, Duration period) {
		if (period.isZero() || period.isNegative() || period.getNano() != 0) {
			throw new IllegalArgumentException(name + "은 0 보다 큰 초 단위여야 한다: " + period);
		}
	}

}
