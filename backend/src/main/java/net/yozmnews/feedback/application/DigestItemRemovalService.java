package net.yozmnews.feedback.application;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.DigestItemRemoval;
import net.yozmnews.feedback.domain.record.ItemRecordRepository;
import net.yozmnews.feedback.domain.record.RemovalFence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DigestItemRemoval} 의 구현 — 지운 항목의 항목 기록을 지운다. docs/specs/feedback.md §4 · feedback-R5 · feedback-R6. 역할
 * 애너테이션이 없다(두 역할 공통).
 * <p>
 * 부르는 쪽({@code digest} 의 지운 기사 따르기)의 트랜잭션에 들어 울타리를 배타로 잡는다. 부르는 쪽은 다이제스트 행을 잠근 뒤 부르고, 기록은
 * 울타리를 먼저 잡되 다이제스트 행을 잠그지 않아 둘 사이에 교착이 없다.
 */
@Service
class DigestItemRemovalService implements DigestItemRemoval {

	private final RemovalFence fence;

	private final ItemRecordRepository itemRecords;

	DigestItemRemovalService(RemovalFence fence, ItemRecordRepository itemRecords) {
		this.fence = Objects.requireNonNull(fence, "fence");
		this.itemRecords = Objects.requireNonNull(itemRecords, "itemRecords");
	}

	@Override
	@Transactional
	public void forget(Map<Long, ? extends Collection<EventId>> itemKeysByDigest) {
		Objects.requireNonNull(itemKeysByDigest, "itemKeysByDigest");
		if (itemKeysByDigest.values().stream().allMatch(Collection::isEmpty)) {
			return;
		}
		this.fence.enterExclusive();
		this.itemRecords.removeAll(itemKeysByDigest);
	}

}
