package net.yozmnews.platform;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

/**
 * 앱의 지금 — 앱 전체가 함께 쓰는 시계 정본 · docs/specs/platform.md §3
 * <p>
 * 이 타입의 bean 은 하나다. 지금만 읽는 곳은 그 bean 을 {@link InstantSource} 로 받고, 진행 중 작업의 멈춤이나 걸린 시간을 재는 곳은 이 타입으로
 * 받아 경과 판정을 쓴다. 경과는 두 시각 사이의 길이에서 시계가 넘긴 시간을 뺀 것이다 — 넘긴 기록이 없는 실제 시간의 시계에서는 단순한 차다.
 * {@code java.time.Clock} 을 상속하지 않는다(Spring Modulith 가 {@code Clock} bean 을 이벤트 발행 레지스트리에 집어 쓴다).
 */
public interface AppClock extends InstantSource {

	/** 앱의 지금이 실제 시간인가 — 측정용 시계면 거짓 */
	boolean realTime();

	/** {@code from} 부터 {@code to} 까지 넘긴 시간을 뺀 경과 — {@code to} 가 {@code from} 보다 앞서거나 같으면 0 */
	Duration elapsed(Instant from, Instant to);

	/** {@code at} 까지의 경과가 {@code elapsed} 이상인 가장 늦은 시각 — 넘긴 기록이 없으면 {@code at − elapsed} */
	Instant before(Instant at, Duration elapsed);

	/**
	 * {@code from} 부터의 경과가 {@code elapsed} 가 되는 가장 이른 시각 — 지금까지의 넘긴 기록으로 계산한다. 넘긴 기록이 없으면
	 * {@code from + elapsed}
	 */
	Instant after(Instant from, Duration elapsed);

	/** {@code from} 부터 지금까지 넘긴 시간을 뺀 경과 */
	default Duration elapsedSince(Instant from) {
		return elapsed(from, instant());
	}

	/** 지금까지의 경과가 {@code elapsed} 이상인 가장 늦은 시각 — 넘긴 기록이 없으면 지금 − {@code elapsed} */
	default Instant before(Duration elapsed) {
		return before(instant(), elapsed);
	}

	/**
	 * 길이 {@code term} 의 임대가 지났는지 가르는 만료 시각의 상한 — 점유 시각 c 에 쓴 만료 시각 c + {@code term} 이 이 값 이하면 지났다(c 부터
	 * 지금까지의 경과가 {@code term} 이상인 것과 같다). 넘긴 기록이 없으면 지금이다.
	 */
	default Instant expiredBy(Duration term) {
		return before(term).plus(term);
	}

}
