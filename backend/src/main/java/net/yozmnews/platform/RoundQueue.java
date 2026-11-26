package net.yozmnews.platform;

import java.util.Objects;

/**
 * 회차 하나의 대기 현황 — 각 모듈의 처리 현황 조회가 낸다 · docs/specs/platform.md §3 · 반복 회차.
 *
 * @param round 회차 이름
 * @param waiting 회차가 처리할 남은 항목 — 점유 중 · 미룬 것 포함
 * @param deferred 남은 항목 가운데 다시 볼 시각이 아직 오지 않은 것 — {@code waiting} 에 든다
 * @param recent 회차 기록의 최근 합
 */
public record RoundQueue(String round, long waiting, long deferred, RoundSummary recent) {

	/**
	 * @throws IllegalArgumentException 건수가 음수이거나 미룬 것이 남은 것보다 많을 때
	 */
	public RoundQueue {
		Objects.requireNonNull(round, "round");
		Objects.requireNonNull(recent, "recent");
		if (waiting < 0 || deferred < 0) {
			throw new IllegalArgumentException("건수는 음수가 아니다");
		}
		if (deferred > waiting) {
			throw new IllegalArgumentException("미룬 것은 남은 것에 든다: " + deferred + " > " + waiting);
		}
	}

}
