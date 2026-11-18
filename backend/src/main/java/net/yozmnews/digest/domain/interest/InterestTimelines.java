package net.yozmnews.digest.domain.interest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.digest.InterestPoint;
import net.yozmnews.digest.TopicSubscriptionStep;
import net.yozmnews.user.TopicSubscriptionChange;

/**
 * 관심사 히스토리의 기준 시각마다 관심과 변화 — 정본 — docs/specs/digest.md §5 · digest-R35 · digest-R36
 * <p>
 * 기준 시각마다 창 끝을 그 기준 시각으로 둔 관심({@link InterestWindow#levels})을 내고, 앞 기준 시각과 견준 새로 생긴 관심 · 사라진 관심
 * ({@link InterestShifts#between})과 두 기준 시각 사이(앞 포함 · 뒤 제외)에 사용자가 직접 바꾼 주제 구독
 * ({@link InterestSignals#subscriberTopicChanges})을 붙인다. 맨 앞 기준 시각은 견줌용이라 결과에 없다.
 */
public final class InterestTimelines {

	private InterestTimelines() {
	}

	/**
	 * @param record 첫 기준 시각의 창 시작부터 마지막 기준 시각까지를 덮는 재료
	 * @param references 기준 시각 — 둘 이상, 시간 순(같은 시각 허용 — 그 사이는 변화 · 구독 변경이 없다)
	 * @return 둘째부터의 기준 시각마다 하나
	 * @throws IllegalArgumentException 기준 시각이 둘보다 적거나 시간 순이 아닐 때(앞이 뒤보다 늦을 때)
	 */
	public static List<InterestPoint> points(InterestRecord record, List<Instant> references) {
		Objects.requireNonNull(record, "record");
		requireReferences(references);
		List<TopicSubscriptionChange> changes = InterestSignals.subscriberTopicChanges(record.topicChanges());
		List<InterestPoint> points = new ArrayList<>();
		InterestLevels before = InterestWindow.levels(record, references.get(0), references.get(0));
		for (int i = 1; i < references.size(); i++) {
			Instant from = references.get(i - 1);
			Instant reference = references.get(i);
			InterestLevels levels = InterestWindow.levels(record, reference, reference);
			InterestShifts.Shift shift = InterestShifts.between(before, levels);
			List<TopicSubscriptionStep> steps = changes.stream()
				.filter(change -> !change.occurredAt().isBefore(from) && change.occurredAt().isBefore(reference))
				.map(change -> new TopicSubscriptionStep(change.occurredAt(), change.action(), change.subtopics()))
				.toList();
			points.add(new InterestPoint(reference, levels.levels(), shift.entered(), shift.left(), steps));
			before = levels;
		}
		return List.copyOf(points);
	}

	/**
	 * 기준 시각 목록의 조건 — 둘 이상, 시간 순(같은 시각 허용). 같은 기준 시각은 창이 같아 관심이 같고 그 사이의 구독 변경이
	 * 없다 — 기간이 시작되는 정각에 부르면 진행 중 기간의 기준 시각이 앞 기간 끝과 같다
	 * @throws IllegalArgumentException 조건에 맞지 않을 때
	 */
	public static void requireReferences(List<Instant> references) {
		Objects.requireNonNull(references, "references");
		if (references.size() < 2) {
			throw new IllegalArgumentException("기준 시각이 둘 이상이어야 한다: " + references.size());
		}
		for (int i = 1; i < references.size(); i++) {
			if (references.get(i - 1).isAfter(references.get(i))) {
				throw new IllegalArgumentException("기준 시각이 시간 순이 아니다: " + references);
			}
		}
	}

	/** 드러난 주제 — {@link InterestShifts#prominent} 를 기준 시각들의 관심에 적용한 것 */
	public static Set<String> prominent(List<InterestPoint> points) {
		Objects.requireNonNull(points, "points");
		return InterestShifts.prominent(points.stream().map(point -> new InterestLevels(point.levels())).toList());
	}

}
