package net.yozmnews.platform.domain;

import java.time.Duration;
import java.util.Objects;

import org.springframework.modulith.events.EventPublication;

/**
 * 미완료 이벤트 발행 가운데 무엇을 다시 처리할지 정한다. 다시 처리하는 것은 리스너가 실패한 발행뿐이다 — 처리 도중
 * 멈춘 발행은 Modulith staleness 감시가 발행 시각에서 멈춤 기준 시간이 지나면 실패로 바꾼 뒤 여기서 고른다.
 */
public final class EventResubmissionPolicy {

	/**
	 * 한 발행의 총 처리 시도 상한 — 최초 처리 1회와 재제출 5회. 발행의 {@code completionAttempts} 는 저장 때 1 이고
	 * 재제출마다 1 씩 는다.
	 */
	public static final int MAX_ATTEMPTS = 6;

	/**
	 * 재제출 주기와 멈춤 기준 시간이 함께 지켜야 할 조건을 확인한다. 감시는 발행 시각으로 멈춤을 판정하므로, 마지막
	 * 재제출까지 걸리는 시간 (MAX_ATTEMPTS − 1) × 재제출 주기가 멈춤 기준보다 짧아야 정상 운영에서 재처리 중인 발행을
	 * 멈춤으로 오판하지 않는다.
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

	public enum Decision {

		/** 다시 처리한다. */
		RESUBMIT,

		/** 실패했지만 시도 상한에 이르렀다 — 다시 처리하지 않고 알린다. */
		EXHAUSTED,

		/** 다시 처리할 대상이 아니다. */
		SKIP

	}

}
