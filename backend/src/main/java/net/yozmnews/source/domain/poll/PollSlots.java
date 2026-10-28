package net.yozmnews.source.domain.poll;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 같은 소스에서 이번 회차에 수집할 피드들의 예약 시각 — 앞서 예약한 수집과 서로를 {@link PollSchedule#SOURCE_SPACING} 만큼 띄운다.
 * 작업 서버는 예약 작업을 poll 간격 단위로 묶어 시작하므로 이 분산만으로는 요청 간격이 보장되지 않는다 — 보장은
 * {@link PollSchedule#reserveFetch} 가 하고, 이 분산은 같은 소스의 작업들이 한꺼번에 worker 를 잡고 기다리지 않게 한다.
 */
public final class PollSlots {

	private PollSlots() {
	}

	/**
	 * @param now 지금
	 * @param lastScheduled 그 소스에서 점유 중인 수집의 마지막 예약 시각 — 없으면 null
	 * @param count 이번 회차에 예약할 수
	 * @return 예약 시각 count 개 — 첫 값은 max(now, lastScheduled + 간격), 이후 간격마다
	 * @throws IllegalArgumentException count 가 음수일 때
	 */
	public static List<Instant> assign(Instant now, @Nullable Instant lastScheduled, int count) {
		Objects.requireNonNull(now, "now");
		if (count < 0) {
			throw new IllegalArgumentException("예약할 수는 0 이상이다: " + count);
		}
		Instant next = now;
		if (lastScheduled != null) {
			Instant afterLast = lastScheduled.plus(PollSchedule.SOURCE_SPACING);
			if (afterLast.isAfter(next)) {
				next = afterLast;
			}
		}
		List<Instant> slots = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			slots.add(next);
			next = next.plus(PollSchedule.SOURCE_SPACING);
		}
		return List.copyOf(slots);
	}

}
