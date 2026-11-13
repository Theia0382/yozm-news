package net.yozmnews.storyline.domain.relation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.storyline.domain.linking.Scored;
import org.jspecify.annotations.Nullable;

/**
 * 관계를 물을 후보 쌍 — 정본 · docs/specs/storyline.md §4 · storyline-R27 · storyline-R28 · storyline-R30.
 * <p>
 * 스토리라인의 상대는 사건을 함께 가진 스토리라인과 점수가 후보 점수 기준 이상인 스토리라인이다. 두 스토리라인 모두 구성 사건 수가 사건 수
 * 하한 이상이어야 하고, 다시 물을 때가 된 쌍만 든다. 사건 수는 직접 소속의 수다.
 */
public final class RelationCandidates {

	private RelationCandidates() {
	}

	/**
	 * 물을 상대 — 사건을 함께 가진 쌍이 먼저(함께 가진 사건 수 내림차순, 같으면 스토리라인 ID 순), 그다음 점수 내림차순(같으면 ID 순).
	 * @param events 이 스토리라인의 구성 사건 수
	 * @param sharing 사건을 함께 가진 스토리라인 → 함께 가진 사건 수
	 * @param scored 점수를 매긴 스토리라인 — 기준 미만은 여기서 거른다
	 * @param eventCounts 상대 스토리라인 → 구성 사건 수 — 없는 상대는 후보가 아니다
	 * @param askedAtSmallerEvents 물은 상대 → 물었을 때 작은 쪽의 구성 사건 수
	 */
	public static List<Long> partners(long storylineId, int events, Map<Long, Integer> sharing, List<Scored> scored,
			Map<Long, Integer> eventCounts, Map<Long, Integer> askedAtSmallerEvents, RelationCriteria criteria) {
		if (events < criteria.minEvents()) {
			return List.of();
		}
		Set<Long> ordered = new LinkedHashSet<>();
		sharing.entrySet()
			.stream()
			.filter(entry -> entry.getValue() > 0)
			.sorted(Map.Entry.<Long, Integer>comparingByValue(Comparator.reverseOrder())
				.thenComparing(Map.Entry.comparingByKey()))
			.forEach(entry -> ordered.add(entry.getKey()));
		scored.stream()
			.filter(one -> one.score().reaches(criteria.candidateScore()))
			.sorted(Comparator.comparingDouble((Scored one) -> one.score().score())
				.reversed()
				.thenComparingLong(Scored::id))
			.forEach(one -> ordered.add(one.id()));
		List<Long> partners = new ArrayList<>();
		for (long partner : ordered) {
			if (partner != storylineId
					&& askable(events, eventCounts.get(partner), askedAtSmallerEvents.get(partner), criteria)) {
				partners.add(partner);
			}
		}
		return partners;
	}

	/**
	 * 그 상대와의 쌍을 물을 수 있는가 — 두 스토리라인 모두 구성 사건 수가 사건 수 하한 이상이고 다시 물을 때다. 점수를 매기기 전에 거를 때와
	 * {@link #partners} 가 함께 쓰는 정본.
	 * @param partnerEvents 상대의 구성 사건 수 — 모르면 null(후보가 아니다)
	 * @param askedAtSmallerEvents 물었을 때 작은 쪽의 구성 사건 수 — 물은 적이 없으면 null
	 */
	public static boolean askable(int events, @Nullable Integer partnerEvents, @Nullable Integer askedAtSmallerEvents,
			RelationCriteria criteria) {
		return events >= criteria.minEvents() && partnerEvents != null && partnerEvents >= criteria.minEvents()
				&& due(askedAtSmallerEvents, Math.min(events, partnerEvents));
	}

	/**
	 * 다시 물을 때인가 — 물은 적이 없거나, 지금 작은 쪽의 구성 사건 수가 물었을 때의 두 배 이상이다.
	 * @param askedAtSmallerEvents 물었을 때 작은 쪽의 구성 사건 수 — 물은 적이 없으면 null
	 */
	public static boolean due(@Nullable Integer askedAtSmallerEvents, int smallerEventsNow) {
		return askedAtSmallerEvents == null || smallerEventsNow >= 2L * askedAtSmallerEvents;
	}

	/** a 가 요청의 first 인가 — 먼저 시작한(첫 기사 시각이 이른) 쪽, 같으면 ID 가 작은 쪽 */
	public static boolean firstIs(long a, Instant aFirst, long b, Instant bFirst) {
		int order = aFirst.compareTo(bFirst);
		return (order != 0) ? order < 0 : a < b;
	}

	/** a 가 점수의 판정할 쪽인가 — 구성 사건 수가 적은 쪽, 같으면 ID 가 큰 쪽 */
	public static boolean subjectIs(long a, int aEvents, long b, int bEvents) {
		return (aEvents != bEvents) ? aEvents < bEvents : a > b;
	}

}
