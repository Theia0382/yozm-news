package net.yozmnews.digest.domain.selection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 사건의 키(주제 중분류 · 지역)를 정하는 몫 규칙 — 정본 · docs/specs/digest.md §5.
 * <p>
 * 평가 있는 소속 기사 가운데 그 키가 붙은 기사의 몫이 {@link #SHARE_THRESHOLD} 이상이면 사건의 키다. 미분류 기사(키 없음)도 분모에 든다.
 * 기사 둘이 서로 다른 키면 둘 다 사건의 키다.
 */
public final class EventShares {

	/** 사건의 키가 되는 몫의 하한(경계 포함) — 측정 전 목표 */
	public static final double SHARE_THRESHOLD = 0.5;

	private EventShares() {
	}

	/**
	 * @param perArticle 평가 있는 소속 기사마다 그 기사의 키
	 * @return 몫이 {@link #SHARE_THRESHOLD} 이상인 키. 빈 목록이면 빈 집합
	 */
	public static Set<String> keys(List<Set<String>> perArticle) {
		Objects.requireNonNull(perArticle, "perArticle");
		if (perArticle.isEmpty()) {
			return Set.of();
		}
		Map<String, Integer> counts = new HashMap<>();
		perArticle.forEach(keys -> keys.forEach(key -> counts.merge(key, 1, Integer::sum)));
		int articles = perArticle.size();
		return counts.entrySet()
			.stream()
			.filter(entry -> (double) entry.getValue() / articles >= SHARE_THRESHOLD)
			.map(Map.Entry::getKey)
			.collect(Collectors.toUnmodifiableSet());
	}

}
