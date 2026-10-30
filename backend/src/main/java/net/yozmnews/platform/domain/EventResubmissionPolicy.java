package net.yozmnews.platform.domain;

import java.time.Duration;
import java.util.Objects;

import org.springframework.modulith.events.EventPublication;

/**
 * 재처리 대상 판정 — 정본 · docs/specs/platform.md §4 · platform-R1 · platform-R3
 */
public final class EventResubmissionPolicy {

	/** 한 발행의 총 처리 시도 상한 — 발행의 {@code completionAttempts} 와 비교한다 */
	public static final int MAX_ATTEMPTS = 6;

	/**
	 * 재제출 주기와 멈춤 기준 시간의 조건(platform-R3)을 확인하고 정책을 만든다.
	 * @throws NullPointerException 두 값 중 하나가 없을 때
	 * @throws IllegalArgumentException 두 값 중 하나가 0 이하이거나 조건을 어길 때
	 */
	public EventResubmissionPolicy(Duration resubmissionInterval, Duration staleAfter) {
		Objects.requireNonNull(resubmissionInterval, "resubmissionInterval");
		Objects.requireNonNull(staleAfter, "staleAfter");
		if (!resubmissionInterval.isPositive() || !staleAfter.isPositive()) {
			throw new IllegalArgumentException(
					"재제출 주기와 멈춤 기준은 0 보다 커야 한다: " + resubmissionInterval + ", " + staleAfter);
		}
		Duration untilLastResubmission = resubmissionInterval.multipliedBy(MAX_ATTEMPTS - 1);
		if (untilLastResubmission.compareTo(staleAfter) >= 0) {
			throw new IllegalArgumentException("마지막 재제출까지 걸리는 시간(" + untilLastResubmission
					+ ")이 멈춤 기준(" + staleAfter + ")보다 짧아야 한다");
		}
	}

	public Decision decide(EventPublication publication) {
		return switch (publication.getStatus()) {
			case FAILED -> (publication.getCompletionAttempts() < MAX_ATTEMPTS) ? Decision.RESUBMIT : Decision.EXHAUSTED;
			case PUBLISHED, PROCESSING, RESUBMITTED, COMPLETED -> Decision.SKIP;
		};
	}

	/** 재처리 판정 */
	public enum Decision {

		/** 다시 처리한다 */
		RESUBMIT,

		/** 실패했지만 시도 상한에 이르렀다 — 다시 처리하지 않고 알린다 */
		EXHAUSTED,

		/** 다시 처리할 대상이 아니다 */
		SKIP

	}

}
