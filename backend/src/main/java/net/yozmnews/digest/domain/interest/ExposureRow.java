package net.yozmnews.digest.domain.interest;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.event.EventId;

/**
 * 노출 행 하나 — 그 사용자의 다이제스트 하나의 항목 하나가 사건 하나를 보였다. 같은 사건이 여러 다이제스트 · 항목에 있으면 여러 행이다.
 * docs/specs/digest.md §5 · digest-R24
 *
 * @param digestId 그 다이제스트
 * @param event 보인 사건 — 저장소가 읽은 행은 섹션에 쓴 사건, 관심도 재료({@link InterestRecord})의 행은 지금 사건으로 푼 것
 * @param exposedAt 그 다이제스트를 끝낸 시각
 * @param share 항목 무게 1 을 그 항목의 사건 행 수 n 으로 나눈 몫 1/n — 0 보다 크고 1 이하
 */
public record ExposureRow(long digestId, EventId event, Instant exposedAt, double share) {

	public ExposureRow {
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(exposedAt, "exposedAt");
		Exposure.requireShare(share);
	}

	/** 같은 다이제스트 · 시각 · 몫으로 사건만 바꾼 행 */
	public ExposureRow withEvent(EventId current) {
		return new ExposureRow(this.digestId, current, this.exposedAt, this.share);
	}

}
