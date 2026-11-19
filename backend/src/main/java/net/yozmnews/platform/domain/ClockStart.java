package net.yozmnews.platform.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * 앱 시계의 기동 판정 — 정본 · docs/specs/platform.md §4
 * <p>
 * 측정용 시계는 측정 profile 과 DB 의 시계 행이 함께 있을 때만 뜬다. 시계 행이 있는 DB 는 측정 profile 없이 뜨지 않는다 — 측정용 시계로 쓴 행은
 * 시각이 실제와 다르다. 측정용 시계는 한 프로세스가 두 역할을 함께 할 때만 뜬다 — 상태를 메모리에 두고 넘길 때 DB 와 함께 바꾸므로, 역할을 나눈
 * 프로세스는 같은 지금을 보지 못한다.
 */
public final class ClockStart {

	private ClockStart() {
	}

	/**
	 * 실제 시간의 시계로 뜰 수 있는지.
	 * @param rowExists DB 에 시계 행이 있는가
	 * @param measurementProfile 측정 profile 이름 — 거부 메시지에 쓴다
	 * @throws IllegalStateException 시계 행이 있을 때
	 */
	public static void requireNoMeasurementRow(boolean rowExists, String measurementProfile) {
		Objects.requireNonNull(measurementProfile, "measurementProfile");
		if (rowExists) {
			throw new IllegalStateException("이 DB 에는 측정용 시계 행(measurement_clock)이 있다 — 측정용 시계로 돈 DB 라 기록된 시각이 실제와"
					+ " 다르다. 측정 인스턴스면 profile " + measurementProfile + " 를 함께 켜고, 운영 DB 로 쓰려던 것이면 측정 전의 덤프로 되돌린다");
		}
	}

	/**
	 * 측정용 시계로 뜰 수 있는지.
	 * @param roles 이 프로세스가 맡는 역할
	 * @param state DB 에 저장된 상태 — 시계 행이 없으면 빈 값
	 * @param measurementProfile 측정 profile 이름 — 거부 메시지에 쓴다
	 * @return 저장된 상태
	 * @throws IllegalStateException 두 역할을 함께 하지 않거나 시계 행이 없을 때
	 */
	public static MeasurementClockState measurement(ActiveRoles roles, Optional<MeasurementClockState> state,
			String measurementProfile) {
		Objects.requireNonNull(roles, "roles");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(measurementProfile, "measurementProfile");
		if (!(roles.web() && roles.worker())) {
			throw new IllegalStateException("측정용 시계는 한 프로세스가 두 역할을 함께 할 때만 뜬다 — profile 을 web,worker,"
					+ measurementProfile + " 로 켠다");
		}
		return state.orElseThrow(() -> new IllegalStateException("profile " + measurementProfile
				+ " 인데 측정용 시계 행이 없다 — 측정 DB 에 INSERT INTO measurement_clock (started_at) VALUES"
				+ " ('2026-11-10T07:05:00+09:00'); 처럼 출발 시각을 넣은 뒤 다시 띄운다"));
	}

}
