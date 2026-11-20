package net.yozmnews.feedback.application;

import java.util.List;
import java.util.Objects;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.domain.record.FeedbackRepository;
import net.yozmnews.feedback.domain.record.RemovalFence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지운 사건 따르기 — 그 사건의 현재 상태 · 이력 행을 모두 지운다. 항목 기록은 건드리지 않는다. docs/specs/feedback.md §4 · feedback-R5 ·
 * feedback-R6
 * <p>
 * 한 트랜잭션에서 울타리를 배타로 잡고 지운다 — 진행 중이던 기록이 먼저 커밋되고, 그 뒤에 시작한 기록은 그 사건이 지금 없어 사건 기록을 쓰지
 * 않는다. 다시 처리해도 결과가 같다.
 */
@Service
class FeedbackRemoval {

	private final RemovalFence fence;

	private final FeedbackRepository feedback;

	FeedbackRemoval(RemovalFence fence, FeedbackRepository feedback) {
		this.fence = Objects.requireNonNull(fence, "fence");
		this.feedback = Objects.requireNonNull(feedback, "feedback");
	}

	@Transactional
	void apply(List<EventId> removed) {
		this.fence.enterExclusive();
		this.feedback.removeAll(removed);
	}

}
