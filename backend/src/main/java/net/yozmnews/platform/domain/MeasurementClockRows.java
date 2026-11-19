package net.yozmnews.platform.domain;

import java.time.Instant;
import java.util.Optional;

/** 측정용 시계의 시계 행과 넘긴 기록을 읽고 쓴다 — docs/specs/platform.md §4 */
public interface MeasurementClockRows {

	/** 시계 행이 있는가 */
	boolean exists();

	/**
	 * 시계 행과 넘긴 기록을 읽는다.
	 * @return 시계 행이 없으면 빈 값. 넘긴 기록은 시간 순이다
	 */
	Optional<MeasurementClockState> load();

	/**
	 * 출발점에 대응하는 실제 시각을 쓴다 — 이미 있으면 그대로 둔다.
	 * @param realNow 지금의 실제 시각
	 */
	void anchor(Instant realNow);

	/** 넘긴 구간 하나를 더한다 */
	void addJump(ClockJumps.Jump jump);

}
