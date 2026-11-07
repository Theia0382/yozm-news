package net.yozmnews.event.application;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.event.AssignmentCriteria;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventReview;
import net.yozmnews.event.JudgementUse;
import net.yozmnews.event.MergePair;
import net.yozmnews.event.MergedEvent;
import net.yozmnews.event.domain.event.EventQueryRepository;
import org.springframework.stereotype.Service;

/**
 * 공개 API {@link EventReview} 의 구현 — docs/specs/event.md §4 · event-R22.
 * <p>
 * 판정 원본의 쓰임은 배정과 병합 판정 기록을 각각 원본 ID 로 찾아 합친다 — 한 원본을 둘이 함께 가리키면 불변식 위반이라 크게 실패한다.
 * 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class EventReviewService implements EventReview {

	private final EventQueryRepository events;

	private final AssignmentCriteria criteria;

	EventReviewService(EventQueryRepository events, AssignmentSettings settings) {
		this.events = events;
		this.criteria = settings.criteria();
	}

	@Override
	public AssignmentCriteria assignmentCriteria() {
		return this.criteria;
	}

	@Override
	public List<MergedEvent> mergedInto(EventId survivor) {
		Objects.requireNonNull(survivor, "survivor");
		return this.events.mergedInto(survivor.value());
	}

	@Override
	public Map<EventJudgementId, JudgementUse> judgementUses(Collection<EventJudgementId> judgements) {
		if (judgements.isEmpty()) {
			return Map.of();
		}
		List<Long> ids = judgements.stream().map(EventJudgementId::value).distinct().toList();
		Map<Long, JudgementUse.Assignment> assignments = this.events.assignmentsByJudgement(ids);
		Map<Long, List<MergePair>> merges = this.events.mergePairsByJudgement(ids);
		Map<EventJudgementId, JudgementUse> uses = new LinkedHashMap<>();
		for (long id : ids) {
			JudgementUse.Assignment assignment = assignments.get(id);
			List<MergePair> pairs = merges.get(id);
			if (assignment != null && pairs != null) {
				throw new IllegalStateException("판정 원본 하나를 배정과 병합 판정 기록이 함께 가리킨다: " + id);
			}
			if (assignment != null) {
				uses.put(new EventJudgementId(id), assignment);
			}
			else if (pairs != null) {
				uses.put(new EventJudgementId(id), new JudgementUse.Merge(pairs));
			}
		}
		return Collections.unmodifiableMap(uses);
	}

}
