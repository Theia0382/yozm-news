package net.yozmnews.feedback.application;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.EventFeedback;
import net.yozmnews.feedback.FeedbackChange;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.feedback.domain.record.FeedbackRepository;
import net.yozmnews.feedback.domain.record.HistoryRow;
import net.yozmnews.feedback.domain.record.MergedValue;
import net.yozmnews.platform.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비자 조회 {@link EventFeedback} 의 구현 — docs/specs/feedback.md §4 · feedback-R8. 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class EventFeedbackService implements EventFeedback {

	private final FeedbackRepository feedback;

	private final EventResolution resolution;

	EventFeedbackService(FeedbackRepository feedback, EventResolution resolution) {
		this.feedback = Objects.requireNonNull(feedback, "feedback");
		this.resolution = Objects.requireNonNull(resolution, "resolution");
	}

	@Override
	@Transactional(readOnly = true)
	public Set<EventId> dislikedAmong(UserId user, Collection<EventId> events) {
		Objects.requireNonNull(user, "user");
		List<EventId> distinct = events.stream().distinct().toList();
		if (distinct.size() > MAX_EVENTS) {
			throw new IllegalArgumentException("한 번에 묻는 사건은 " + MAX_EVENTS + "개까지다: " + distinct.size());
		}
		if (distinct.isEmpty()) {
			return Set.of();
		}
		Map<EventId, EventId> currentOf = this.resolution.recordedOf(distinct);
		Map<EventId, FeedbackValue> values = MergedValue.of(this.feedback.lastChanges(user, currentOf.keySet()),
				currentOf);
		Set<EventId> disliked = new LinkedHashSet<>();
		for (EventId event : distinct) {
			if (values.get(event) == FeedbackValue.DISLIKE) {
				disliked.add(event);
			}
		}
		return Collections.unmodifiableSet(disliked);
	}

	@Override
	@Transactional(readOnly = true)
	public List<FeedbackChange> changes(UserId user, Instant from, Instant to) {
		Objects.requireNonNull(user, "user");
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("기간의 시작이 끝보다 늦다: " + from + " > " + to);
		}
		List<HistoryRow> rows = this.feedback.history(user, from, to);
		Map<EventId, EventId> current = this.resolution.currentOf(rows.stream().map(HistoryRow::event).toList());
		return rows.stream()
			.map(row -> new FeedbackChange(row.event(), current.get(row.event()), row.value(), row.digestId(),
					row.occurredAt()))
			.toList();
	}

}
