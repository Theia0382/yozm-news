package net.yozmnews.source.domain.poll;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 같은 소스의 예약 시각 분산 — docs/specs/source.md §4 · source-R18
 */
public final class PollSlots {

	private PollSlots() {
	}

	/**
	 * 이번 회차의 예약 시각을 정한다.
	 * @param lastScheduled 그 소스에서 점유 중인 수집의 마지막 예약 시각 — 없으면 null
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
