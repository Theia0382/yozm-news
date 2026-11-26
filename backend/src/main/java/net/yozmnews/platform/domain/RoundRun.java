package net.yozmnews.platform.domain;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.platform.RoundEnd;

/**
 * 끝난 회차 하나의 기록 — docs/specs/platform.md §6 {@code round_runs}.
 *
 * @param round 회차 이름 — {@link RoundRules#NAME} 꼴
 * @param startedAt 시작 시각
 * @param endedAt 끝난 시각 — 시작 시각 이후
 * @param batches 부른 뭉치 수 — 첫 뭉치가 예외를 던졌으면 0
 * @param handled 처리한 수
 * @param end 끝난 까닭
 */
public record RoundRun(String round, Instant startedAt, Instant endedAt, int batches, int handled, RoundEnd end) {

	/**
	 * @throws IllegalArgumentException 이름 꼴이 틀렸거나, 끝난 시각이 시작보다 앞서거나, 수가 음수일 때
	 */
	public RoundRun {
		RoundRules.requireName(round);
		Objects.requireNonNull(startedAt, "startedAt");
		Objects.requireNonNull(endedAt, "endedAt");
		Objects.requireNonNull(end, "end");
		if (endedAt.isBefore(startedAt)) {
			throw new IllegalArgumentException("끝난 시각이 시작보다 앞선다: " + startedAt + " > " + endedAt);
		}
		if (batches < 0 || handled < 0) {
			throw new IllegalArgumentException("뭉치 수와 처리 수는 음수가 아니다");
		}
	}

}
