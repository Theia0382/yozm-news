package net.yozmnews.feedback.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.DigestItems;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.feedback.domain.record.FeedbackAcceptance;
import net.yozmnews.feedback.domain.record.FeedbackRepository;
import net.yozmnews.feedback.domain.record.ItemRecordRepository;
import net.yozmnews.feedback.domain.record.RemovalFence;
import net.yozmnews.platform.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 누르기 · 되돌리기 — 다이제스트 항목에 한 번 누르고 그 항목의 사건마다 기록한다. docs/specs/feedback.md §4 · feedback-R2 ~ feedback-R4 ·
 * feedback-R6.
 * <p>
 * 한 트랜잭션에서 울타리를 공유로 잡고, 요청의 사건이 든 항목을 찾아 판정한 뒤, 항목 기록을 쓰고, 항목의 사건 가운데 지금 있는 사건마다 현재
 * 상태를 쓰고 바뀌었으면 이력 한 줄을 더한다. 지금 있는 사건의 확인은 울타리를 잡은 뒤에 한다 — 그래야 지운 사건 따르기가 지나간 뒤 지운 사건을
 * 가리키는 행이 남지 않는다. 합쳐진 사건도 지금 있는 사건이고 원래 ID 로 기록한다.
 * <p>
 * 잠금 순서는 항목 기록 행 → 사건 행(사건 ID 오름차순)이다 — 같은 사건을 담은 서로 다른 항목의 동시 누르기가 교착하지 않는다.
 */
@Service
public class FeedbackRecording {

	private final RemovalFence fence;

	private final DigestItems digestItems;

	private final EventResolution resolution;

	private final ItemRecordRepository itemRecords;

	private final FeedbackRepository feedback;

	FeedbackRecording(RemovalFence fence, DigestItems digestItems, EventResolution resolution,
			ItemRecordRepository itemRecords, FeedbackRepository feedback) {
		this.fence = Objects.requireNonNull(fence, "fence");
		this.digestItems = Objects.requireNonNull(digestItems, "digestItems");
		this.resolution = Objects.requireNonNull(resolution, "resolution");
		this.itemRecords = Objects.requireNonNull(itemRecords, "itemRecords");
		this.feedback = Objects.requireNonNull(feedback, "feedback");
	}

	/**
	 * 그 사용자가 그 다이제스트에서 그 사건이 든 항목에 값을 남긴다 — 항목의 사건 모두를 그 값으로 둔다(없음이면 모두 없음). 시각은 마이크로초까지
	 * 남긴다.
	 * @param event 항목의 사건 하나 — 화면은 항목 키(첫 사건)를 보낸다
	 * @param digestId 누른 다이제스트의 ID — 1 이상
	 * @throws IllegalArgumentException {@code digestId} 가 1 보다 작을 때
	 */
	@Transactional
	public RecordOutcome record(UserId user, EventId event, FeedbackValue value, long digestId, Instant now) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(value, "value");
		Objects.requireNonNull(now, "now");
		if (digestId < 1) {
			throw new IllegalArgumentException("다이제스트 ID 는 1 이상이어야 한다: " + digestId);
		}
		this.fence.enterShared();
		List<EventId> items = this.digestItems.itemOf(user, digestId, event);
		if (!FeedbackAcceptance.accepts(items)) {
			return new RecordOutcome.NotInDigest();
		}
		List<EventId> alive = this.resolution.currentOf(items)
			.keySet()
			.stream()
			.sorted(Comparator.comparingLong(EventId::value))
			.toList();
		Instant at = now.truncatedTo(ChronoUnit.MICROS);
		boolean changed = this.itemRecords.change(user, digestId, items.getFirst(), value, at);
		for (EventId member : alive) {
			if (this.feedback.change(user, member, value, digestId, at)) {
				this.feedback.append(user, member, value, digestId, at);
				changed = true;
			}
		}
		return changed ? new RecordOutcome.Recorded(value) : new RecordOutcome.Unchanged(value);
	}

}
