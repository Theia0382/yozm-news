package net.yozmnews.digest.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

import net.yozmnews.digest.domain.digest.DigestLedger;
import net.yozmnews.digest.domain.schedule.DigestScheduleRules;
import net.yozmnews.digest.domain.schedule.ManualTarget;
import net.yozmnews.platform.UserId;
import org.jspecify.annotations.Nullable;

/**
 * 수동 생성의 대상 · 오늘 횟수 읽기 — 정본. 수동 생성({@link DigestRequestsService})과 수동 생성 상태({@link DigestDirectoryService})가
 * 같은 읽기를 쓴다. docs/specs/digest.md §4 · digest-R31 · digest-R40
 * <p>
 * 트랜잭션 안에서 부른다 — 잠금은 부르는 쪽이 정한다. 스프링 구성 요소가 아니다 — 부르는 쪽이 행 조회로 만든다.
 */
final class ManualTargets {

	private final DigestLedger ledger;

	ManualTargets(DigestLedger ledger) {
		this.ledger = Objects.requireNonNull(ledger, "ledger");
	}

	/** 지금 요청하면 만들 다이제스트의 날짜 · 기간과 다시 만들기인가 — 대상 규칙은 {@link ManualTarget} */
	ManualTarget targetOf(UserId user, LocalDate today, Instant now) {
		@Nullable LocalDate latestDate = this.ledger.latestDate(user).orElse(null);
		return ManualTarget.of(today, now, latestDate,
				(latestDate == null) ? null : this.ledger.latestPeriodOn(user, latestDate).orElse(null),
				this.ledger.latestPeriodOn(user, today).orElse(null), this.ledger.lastCoveredEnd(user).orElse(null));
	}

	/** 오늘(사용자 시간대) 만든 수동 행의 수 — 만든 시각이 [오늘 0시, 다음 날 0시) */
	int manualCountOn(UserId user, LocalDate today, ZoneId zone) {
		return this.ledger.manualCountBetween(user, DigestScheduleRules.startOfDay(today, zone),
				nextDayStart(today, zone));
	}

	/** 다음 날 0시(사용자 시간대) — 하루 횟수가 다시 차는 시각 */
	static Instant nextDayStart(LocalDate today, ZoneId zone) {
		return DigestScheduleRules.startOfDay(today.plusDays(1), zone);
	}

}
