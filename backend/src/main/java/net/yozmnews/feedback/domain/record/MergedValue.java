package net.yozmnews.feedback.domain.record;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.FeedbackValue;

/**
 * 풀린 사건의 값 고르기 — 정본. docs/specs/feedback.md §4 · feedback-R8
 * <p>
 * 지금 사건 하나로 풀리는 기록한 사건들(그 사건과 그 사건으로 합쳐진 사건) 가운데 마지막 이력 행의 ID 가 가장 큰 것, 곧 마지막으로 누른 값을
 * 그 지금 사건의 값으로 한다. 그 값이 없음(되돌리기)이면 그 앞에 다른 사건에 남은 값이 있어도 없음이다.
 */
public final class MergedValue {

	private MergedValue() {
	}

	/**
	 * @param lastChanges 기록한 사건마다 마지막 이력 행
	 * @param currentOf 기록한 사건 → 지금 사건 — 없는 사건의 행은 버린다
	 * @return 지금 사건 → 마지막으로 누른 값(없음 포함) — 행이 없는 지금 사건은 빠진다
	 */
	public static Map<EventId, FeedbackValue> of(Collection<LastChange> lastChanges, Map<EventId, EventId> currentOf) {
		Objects.requireNonNull(lastChanges, "lastChanges");
		Objects.requireNonNull(currentOf, "currentOf");
		Map<EventId, LastChange> latest = new HashMap<>();
		for (LastChange change : lastChanges) {
			EventId current = currentOf.get(change.event());
			if (current == null) {
				continue;
			}
			latest.merge(current, change, (a, b) -> (a.rowId() >= b.rowId()) ? a : b);
		}
		Map<EventId, FeedbackValue> values = new HashMap<>();
		latest.forEach((current, change) -> values.put(current, change.value()));
		return Collections.unmodifiableMap(values);
	}

}
