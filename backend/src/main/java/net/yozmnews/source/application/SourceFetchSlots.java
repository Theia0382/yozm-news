package net.yozmnews.source.application;

import java.time.Duration;
import java.time.InstantSource;

import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.poll.PollSchedule;
import net.yozmnews.source.domain.poll.PollSchedule.FetchSlot;
import net.yozmnews.source.domain.poll.SourceFetchSlotRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 소스의 요청 시작 시각 예약과 그 대기 — docs/specs/source.md §4 · source-R18
 */
@Component
class SourceFetchSlots {

	private final SourceFetchSlotRepository slots;

	private final TransactionTemplate transaction;

	SourceFetchSlots(SourceFetchSlotRepository slots, TransactionTemplate transaction) {
		this.slots = slots;
		this.transaction = transaction;
	}

	/** 소스의 요청 시작 시각을 한 트랜잭션에서 예약한다 — 지금 시각은 행을 잠근 뒤에 읽는다 */
	FetchSlot reserve(SourceId source, InstantSource clock) {
		return this.transaction.execute(status -> {
			FetchSlot slot = PollSchedule.reserveFetch(this.slots.lockNextFetchAt(source), clock.instant());
			this.slots.saveNextFetchAt(source, slot.next());
			return slot;
		});
	}

	/**
	 * 주어진 시간만큼 기다린다.
	 * @return 기다렸으면 true, 기다리다 중단되면 false — 인터럽트 표시는 되살린다
	 */
	static boolean sleep(Duration wait) {
		if (wait.isZero()) {
			return true;
		}
		try {
			Thread.sleep(wait);
			return true;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

}
