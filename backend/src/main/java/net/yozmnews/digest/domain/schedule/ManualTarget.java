package net.yozmnews.digest.domain.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.digest.domain.digest.DigestPeriod;
import org.jspecify.annotations.Nullable;

/**
 * 수동 생성이 만들 다이제스트의 날짜와 기간 — 정본. docs/specs/digest.md §5
 * <p>
 * 오늘(사용자 시간대)보다 늦은 날짜의 다이제스트가 있으면(시간대를 서쪽으로 바꾼 뒤) 그 가장 늦은 날짜를 같은 기간으로 다시 만든다 — 날짜를
 * 거꾸로 만들지 않는다. 아니면 오늘 날짜의 다이제스트가 있으면 그 기간으로 다시 만들고, 없으면 오늘 날짜로 요청 시각까지의 다음 기간
 * ({@link DigestPeriods#next})을 새로 만든다. 다음 기간이 비면(직전 끝이 요청 시각보다 늦다 — 프로세스 사이 시계 차이에서만) 가장 늦은
 * 다이제스트를 다시 만든다.
 *
 * @param date 만들 다이제스트의 날짜
 * @param period 그 기간
 * @param regenerated 그 날짜의 다이제스트를 같은 기간으로 다시 만드는가
 */
public record ManualTarget(LocalDate date, DigestPeriod period, boolean regenerated) {

	public ManualTarget {
		Objects.requireNonNull(date, "date");
		Objects.requireNonNull(period, "period");
	}

	/**
	 * @param today 요청 시각의 사용자 시간대 날짜
	 * @param now 요청 시각 — 새 기간의 끝
	 * @param latestDate 그 사용자의 가장 늦은 다이제스트 날짜(계기 · 상태 무관) — 없으면 null
	 * @param latestPeriod 가장 늦은 날짜의 가장 최근 다이제스트 기간 — 날짜가 없으면 null
	 * @param todayPeriod 오늘 날짜의 가장 최근 다이제스트 기간 — 없으면 null
	 * @param lastCoveredEnd 직전에 끝난 다이제스트({@code READY} · {@code EMPTY})의 가장 늦은 기간 끝 — 없으면 null
	 */
	public static ManualTarget of(LocalDate today, Instant now, @Nullable LocalDate latestDate,
			@Nullable DigestPeriod latestPeriod, @Nullable DigestPeriod todayPeriod, @Nullable Instant lastCoveredEnd) {
		Objects.requireNonNull(today, "today");
		Objects.requireNonNull(now, "now");
		if (latestDate != null && latestDate.isAfter(today)) {
			return new ManualTarget(latestDate, Objects.requireNonNull(latestPeriod, "latestPeriod"), true);
		}
		if (todayPeriod != null) {
			return new ManualTarget(today, todayPeriod, true);
		}
		Optional<DigestPeriod> next = DigestPeriods.next(now, lastCoveredEnd);
		if (next.isPresent()) {
			return new ManualTarget(today, next.get(), false);
		}
		if (latestDate == null || latestPeriod == null) {
			// 끝난 다이제스트가 있으면 가장 늦은 날짜도 있다 — 직전 끝이 없으면 다음 기간은 비지 않는다
			throw new IllegalStateException("다음 기간이 비었는데 다시 만들 다이제스트가 없다");
		}
		return new ManualTarget(latestDate, latestPeriod, true);
	}

}
