package net.yozmnews.event.domain.event;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import net.yozmnews.event.EventStatus;

/**
 * 사건 닫기 판정의 정본 — 마지막으로 기사가 붙은 뒤 닫는 기간이 지났거나, 연 뒤 최대 기간이 지난 열린 사건을 닫는다 · docs/specs/event.md §4 ·
 * event-R13.
 * <p>
 * 기준 시각은 발행 시각이 아니라 이 인스턴스에서 일어난 일의 시각이다 — 늦게 처리되는 기사(채우기 · 적체)가 연 사건이 열자마자 닫히지 않는다.
 * 경계는 포함한다. 값은 설정이 정한다 — 기본값과 그 근거는 docs/specs/event.md §7.
 *
 * @param closeAfter 닫는 기간 — 마지막으로 기사가 붙은 뒤 이만큼 새 기사가 없으면 닫는다
 * @param maxOpen 최대 기간 — 연 뒤 이만큼 지나면 새 기사가 있어도 닫는다
 */
public record EventClosing(Duration closeAfter, Duration maxOpen) {

	public EventClosing {
		Objects.requireNonNull(closeAfter, "closeAfter");
		Objects.requireNonNull(maxOpen, "maxOpen");
	}

	/**
	 * 검증한 닫기 기준.
	 * @param window 사건 기준의 시간 창 — 닫는 기간은 이보다 짧을 수 없다(제때 기사가 붙을 수 있는 사건이 닫히지 않게)
	 * @throws IllegalArgumentException 둘 가운데 하나가 0 이하이거나 초 단위가 아니거나, 닫는 기간이 창보다 짧거나, 최대 기간이 닫는 기간보다
	 * 짧을 때
	 */
	public static EventClosing of(Duration closeAfter, Duration maxOpen, Duration window) {
		for (Duration value : new Duration[] { closeAfter, maxOpen }) {
			if (value.isZero() || value.isNegative() || value.getNano() != 0) {
				throw new IllegalArgumentException("닫는 기간 · 최대 기간은 0 보다 큰 초 단위여야 한다: " + value);
			}
		}
		if (closeAfter.compareTo(window) < 0) {
			throw new IllegalArgumentException("닫는 기간은 시간 창 이상이어야 한다: 닫는 기간 " + closeAfter + " · 창 " + window);
		}
		if (maxOpen.compareTo(closeAfter) < 0) {
			throw new IllegalArgumentException("최대 기간은 닫는 기간 이상이어야 한다: 최대 기간 " + maxOpen + " · 닫는 기간 " + closeAfter);
		}
		return new EventClosing(closeAfter, maxOpen);
	}

	/** 닫을 사건인가 — 열려 있고, 마지막으로 붙은 시각 ≤ now − 닫는 기간이거나 연 시각 ≤ now − 최대 기간 */
	public boolean due(LockedEvent event, Instant now) {
		return event.status() == EventStatus.OPEN
				&& (!event.lastJoinedAt().isAfter(idleBefore(now)) || !event.openedAt().isAfter(openedBefore(now)));
	}

	/** 마지막으로 붙은 시각이 이 시각 이하면 닫는다 */
	public Instant idleBefore(Instant now) {
		return now.minus(this.closeAfter);
	}

	/** 연 시각이 이 시각 이하면 닫는다 */
	public Instant openedBefore(Instant now) {
		return now.minus(this.maxOpen);
	}

}
