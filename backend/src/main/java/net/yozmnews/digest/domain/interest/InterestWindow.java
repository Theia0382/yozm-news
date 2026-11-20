package net.yozmnews.digest.domain.interest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.FeedbackChange;
import net.yozmnews.user.StorylineSubscriptionChange;
import net.yozmnews.user.TopicSubscriptionChange;

/**
 * 기준 시각 하나의 관심 — 재료({@link InterestRecord})에서 창 [{@link #start}(기준 시각), 창 끝) 안의 행만 골라 산식에 넘긴다. 정본 —
 * docs/specs/digest.md §5 · digest-R25 · digest-R35
 * <ul>
 * <li>노출은 지금 사건마다 한 번 — 창 안에서 가장 늦은 다이제스트((끝낸 시각, 다이제스트 ID) 가 가장 큰 것)의 끝낸 시각과 몫. 몫은 그
 * 다이제스트에서 그 사건으로 풀린 행들의 몫 합이고 1 을 넘으면 1 이다 — 한 항목의 사건들이 합쳐지면 그 항목 무게가 남는 사건에 모이고, 서로 다른
 * 항목에서 같은 사건으로 풀리면 사건마다 한 번으로 자른다</li>
 * <li>반응은 창 안 이력 가운데 지금 사건이 그 사건인 행의 기록 순 마지막 값 — 창 시작 앞에 누른 반응은 읽지 않으므로, 창 시작을 걸쳐 같은 사건이
 * 다시 노출된 경우에만 그 반응을 없음으로 센다</li>
 * <li>주제 · 스토리라인 구독 변경은 창 안 행</li>
 * </ul>
 * 창 끝은 다이제스트가 부르는 시각(기간 끝 뒤의 노출 · 반응 · 구독 변경도 무게 1 로 센다), 관심사 히스토리가 기준 시각이다(기준 시각 뒤 신호를 넣지
 * 않는다). 구독 제안은 기준 시각과 창 끝이 모두 부르는 시각이다. 창 끝이 창 시작 이하이면 빈 창이다.
 */
public final class InterestWindow {

	private InterestWindow() {
	}

	/** 창 시작 — 기준 시각 − {@link InterestRules#lookback()}. 재료를 읽는 범위의 시작도 이것으로 정한다 */
	public static Instant start(Instant reference) {
		Objects.requireNonNull(reference, "reference");
		return reference.minus(InterestRules.lookback());
	}

	/**
	 * @param record 창을 덮는 범위에서 읽은 재료
	 * @param reference 기준 시각 — 감쇠의 기준
	 * @param windowEnd 창 끝(제외)
	 */
	public static InterestLevels levels(InterestRecord record, Instant reference, Instant windowEnd) {
		Objects.requireNonNull(record, "record");
		Objects.requireNonNull(windowEnd, "windowEnd");
		Instant from = start(reference);
		Map<EventId, Latest> latest = new HashMap<>();
		for (ExposureRow row : record.exposures()) {
			if (within(row.exposedAt(), from, windowEnd)) {
				latest.merge(row.event(), Latest.of(row), Latest::merge);
			}
		}
		Map<EventId, Exposure> exposures = new HashMap<>();
		latest.forEach((event, row) -> exposures.put(event, new Exposure(row.at(), Math.min(1.0, row.share()))));
		List<FeedbackChange> feedback = record.feedback()
			.stream()
			.filter(change -> within(change.occurredAt(), from, windowEnd))
			.toList();
		List<TopicSubscriptionChange> topics = record.topicChanges()
			.stream()
			.filter(change -> within(change.occurredAt(), from, windowEnd))
			.toList();
		List<StorylineSubscriptionChange> storylines = record.storylineChanges()
			.stream()
			.filter(change -> within(change.occurredAt(), from, windowEnd))
			.toList();
		List<SubscriptionSignal> signals = new ArrayList<>(InterestSignals.topics(topics));
		signals.addAll(InterestSignals.storylines(storylines, record.storylineShares()));
		return InterestFormula.levels(InterestSignals.exposed(exposures, feedback, record.subtopics()), signals,
				reference);
	}

	private static boolean within(Instant at, Instant from, Instant to) {
		return !at.isBefore(from) && at.isBefore(to);
	}

	/** 사건 하나의 지금까지 가장 늦은 다이제스트와 그 다이제스트에서 모은 몫 합 */
	private record Latest(Instant at, long digestId, double share) {

		static Latest of(ExposureRow row) {
			return new Latest(row.exposedAt(), row.digestId(), row.share());
		}

		/** 더 늦은 다이제스트의 것을 남기고, 같은 다이제스트면 몫을 더한다 */
		static Latest merge(Latest a, Latest b) {
			int order = a.at().compareTo(b.at());
			if (order == 0) {
				order = Long.compare(a.digestId(), b.digestId());
			}
			if (order == 0) {
				return new Latest(a.at(), a.digestId(), a.share() + b.share());
			}
			return (order > 0) ? a : b;
		}

	}

}
