package net.yozmnews.source;

import java.time.Duration;
import java.util.Objects;

/**
 * 피드 상태의 판정 기준값과 재판정 간격 — 화면이 보이는 값. docs/specs/source.md §3.
 * <p>
 * 값의 정본은 판정 규칙이다({@link SourceAdministration#FEED_STATUS_CRITERIA} — source-R30 · source-R31 · source-R47).
 *
 * @param degradedAfterFailures 이만큼 연속 실패하면 {@code DEGRADED}
 * @param degradedAfterPermanentFailures 연속 실패 가운데 영구 실패가 이만큼이면 {@code DEGRADED}
 * @param staleAfter 새 기사가 이만큼 없으면 {@code STALE}
 * @param recheckInterval 피드 건강 검사가 다시 판정하는 간격
 */
public record FeedStatusCriteria(int degradedAfterFailures, int degradedAfterPermanentFailures, Duration staleAfter,
		Duration recheckInterval) {

	public FeedStatusCriteria {
		if (degradedAfterFailures < 1 || degradedAfterPermanentFailures < 1) {
			throw new IllegalArgumentException(
					"실패 기준은 1 이상이다: " + degradedAfterFailures + " / " + degradedAfterPermanentFailures);
		}
		Objects.requireNonNull(staleAfter, "staleAfter");
		Objects.requireNonNull(recheckInterval, "recheckInterval");
		if (!staleAfter.isPositive() || !recheckInterval.isPositive()) {
			throw new IllegalArgumentException("기간은 양수다: " + staleAfter + " / " + recheckInterval);
		}
	}

}
