package net.yozmnews.digest.domain.interest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 스토리라인의 주제 몫 — 정본 — docs/specs/digest.md §5
 * <p>
 * 스토리라인에 주제를 저장하지 않고, 쓸 때 사건마다 사건의 주제({@link net.yozmnews.digest.domain.selection.EventFacts#subtopics()} —
 * 평가 있는 소속 기사의 몫으로 정한 주제)로 모은다. 몫 = 그 중분류가 붙은 사건 수 ÷ 사건 수. 사건마다 한 번 세어
 * 보도량이 많은 사건에 끌리지 않고, 여러 주제에 걸친 스토리라인은 몫으로 나뉜다 — 그 주제로 볼 기준 몫은 두지 않는다.
 */
public final class StorylineTopics {

	private StorylineTopics() {
	}

	/**
	 * @param eventTopics 평가 있는 소속 기사가 있는 사건마다 사건의 주제 중분류(활성 키) — 주제가 없는 사건(빈 집합)도 분모에 든다
	 * @return 중분류 → 몫. 사건이 없으면 빈 맵
	 */
	public static Map<String, Double> shares(List<Set<String>> eventTopics) {
		Objects.requireNonNull(eventTopics, "eventTopics");
		if (eventTopics.isEmpty()) {
			return Map.of();
		}
		Map<String, Integer> counts = new HashMap<>();
		eventTopics.forEach(subtopics -> subtopics.forEach(subtopic -> counts.merge(subtopic, 1, Integer::sum)));
		Map<String, Double> shares = new HashMap<>();
		counts.forEach((subtopic, count) -> shares.put(subtopic, (double) count / eventTopics.size()));
		return Map.copyOf(shares);
	}

}
