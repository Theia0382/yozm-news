package net.yozmnews.digest.domain.interest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 스토리라인의 주제 몫 — 정본 — docs/specs/digest.md §5
 * <p>
 * 스토리라인에 주제를 저장하지 않고, 쓸 때 사건마다 대표 기사 하나의 주제로 모은다. 몫 = 그 중분류가 붙은 대표 수 ÷ 대표 수. 사건마다 한 번 세어
 * 보도량이 많은 사건에 끌리지 않고, 여러 주제에 걸친 스토리라인은 몫으로 나뉜다 — 그 주제로 볼 기준 몫은 두지 않는다.
 */
public final class StorylineTopics {

	private StorylineTopics() {
	}

	/**
	 * @param representatives 평가가 있는 대표 기사마다 그 주제 중분류(활성 키) — 미분류 대표(빈 집합)도 분모에 든다
	 * @return 중분류 → 몫. 대표가 없으면 빈 맵
	 */
	public static Map<String, Double> shares(List<Set<String>> representatives) {
		Objects.requireNonNull(representatives, "representatives");
		if (representatives.isEmpty()) {
			return Map.of();
		}
		Map<String, Integer> counts = new HashMap<>();
		representatives.forEach(subtopics -> subtopics.forEach(subtopic -> counts.merge(subtopic, 1, Integer::sum)));
		Map<String, Double> shares = new HashMap<>();
		counts.forEach((subtopic, count) -> shares.put(subtopic, (double) count / representatives.size()));
		return Map.copyOf(shares);
	}

}
