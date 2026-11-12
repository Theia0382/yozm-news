package net.yozmnews.event.domain.tally;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventTally;
import net.yozmnews.source.SourceId;

/**
 * 기간 집계의 정본 — 사건 하나 또는 사건 묶음의 기간 안 기사를 센다 · docs/specs/event.md §4 · event-R15.
 * <p>
 * 기사 수는 기간 안 기사 수, 소스 수는 그 기사들의 엔트리 소스 합집합의 크기(소스가 없는 기사는 더하는 것이 없다), 중요도는 중요도가 있는
 * 기사의 평균이다. 평균은 {@link BigDecimal} 로 센다 — 중요도 하한과 같은 수 체계로 비교해 경계에서 이진 소수 오차로 뒤집히지 않는다.
 */
public final class EventTallies {

	private EventTallies() {
	}

	/**
	 * @param members 묶음 키 → 기간 안 기사 — 한 묶음 안에 같은 기사가 두 번 들지 않는다
	 * @param sources 기사 → 엔트리 소스 — 없는 기사는 소스가 없다
	 * @param importances 기사 → 중요도 — 없는 기사는 중요도가 없다
	 * @return 묶음 키 → 집계, {@code members} 의 순서
	 */
	public static <K> Map<K, EventTally> tally(Map<K, List<ArticleId>> members, Map<ArticleId, Set<SourceId>> sources,
			Map<ArticleId, Double> importances) {
		Map<K, EventTally> tallies = new LinkedHashMap<>();
		members.forEach((key, articles) -> {
			Set<SourceId> distinct = new HashSet<>();
			BigDecimal sum = BigDecimal.ZERO;
			int rated = 0;
			for (ArticleId article : articles) {
				distinct.addAll(sources.getOrDefault(article, Set.of()));
				Double importance = importances.get(article);
				if (importance != null) {
					sum = sum.add(BigDecimal.valueOf(importance));
					rated++;
				}
			}
			BigDecimal mean = rated == 0 ? null : sum.divide(BigDecimal.valueOf(rated), MathContext.DECIMAL64);
			tallies.put(key, new EventTally(articles.size(), distinct.size(), mean));
		});
		return Collections.unmodifiableMap(tallies);
	}

}
