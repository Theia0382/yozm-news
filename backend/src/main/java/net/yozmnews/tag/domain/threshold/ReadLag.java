package net.yozmnews.tag.domain.threshold;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 평가가 쓴 저장 기준을 읽은 시각의 범위 — 저장 바닥이 평가 시각에서 거슬러 보는 길이 · docs/specs/tag.md §4 · tag-R32
 * <p>
 * 평가 회차는 저장 기준을 시작 때 읽고 평가 시각을 결과 뒤에 찍는다. 그 사이는 회차의 점유 기간 안이다. 점유 기간은 시계가 넘긴 시간을 뺀 경과로
 * 재므로, 넘긴 기록이 있으면 측정 시각으로는 더 길 수 있다. 넘긴 기록이 없으면 {@link #fixed(Duration)} 과 같다.
 */
public interface ReadLag {

	/** 평가 시각 {@code evaluatedAt} 의 평가가 저장 기준을 읽었을 수 있는 가장 이른 시각 */
	Instant readFrom(Instant evaluatedAt);

	/** 시각 {@code changedAt} 에 바뀐 저장 기준을 모든 평가가 읽었다고 볼 수 있는 가장 이른 평가 시각 */
	Instant settledAt(Instant changedAt);

	/** 측정 시각으로 고정 길이 {@code lag} 인 읽기 지연 — 넘긴 기록이 없는 시계의 지연이다 */
	static ReadLag fixed(Duration lag) {
		Objects.requireNonNull(lag, "lag");
		return new ReadLag() {

			@Override
			public Instant readFrom(Instant evaluatedAt) {
				return evaluatedAt.minus(lag);
			}

			@Override
			public Instant settledAt(Instant changedAt) {
				return changedAt.plus(lag);
			}

		};
	}

}
