package net.yozmnews.event.domain.feature;

import java.time.Duration;
import java.time.Instant;

/**
 * 기사 표현 보관의 정본 — 사건이 닫힌 기사의 표현은 정리 작업이 지우고, 그 모델 버전의 열린 사건에 배정되지 않은 표현은 만든 뒤 시간 창 +
 * 여유만큼 둔다 · docs/specs/event.md §4 · event-R13.
 */
public final class FeatureRetention {

	/** 배정되지 않은 표현을 시간 창 너머로 더 두는 여유 — 측정 전 목표 */
	public static final Duration MARGIN = Duration.ofDays(1);

	private FeatureRetention() {
	}

	/**
	 * 배정되지 않은 표현은 만든 시각이 이 시각보다 이르면 지운다 — {@code now − (창 + 여유)}.
	 * @param window 사건 기준의 시간 창
	 */
	public static Instant unassignedBefore(Instant now, Duration window) {
		return now.minus(window).minus(MARGIN);
	}

}
