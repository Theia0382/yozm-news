package net.yozmnews.digest.domain.selection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventId;

/**
 * 사건 단위 중복 제거 — 정본 — docs/specs/digest.md §5
 * <p>
 * 같은 사건의 구독 후보는 하나만 남긴다 — 자리는 그 사건 후보 가운데 가장 앞 순위, 기사는 그 사건 후보 가운데 대표 기사(event-R14 — 그 사용자의
 * 제외를 지난 후보에서 고른 것). 큰 슬롯이 쓴 사건의 구독 후보는 뺀다. 사건에 배정되지 않은 기사는 그대로 둔다.
 */
public final class EventDedup {

	private EventDedup() {
	}

	/**
	 * 사건마다 그 사건의 구독 후보 — {@code event} 의 대표 기사 조회에 넘길 묶음. 사건 순서는 첫 후보의 순위 순.
	 * @param ranked 순위대로 된 구독 후보
	 * @param eventOf 기사 → 사건 — 배정이 없는 기사는 빠져 있다
	 */
	public static Map<EventId, List<ArticleId>> groups(List<ArticleFacts> ranked, Map<ArticleId, EventId> eventOf) {
		Map<EventId, List<ArticleId>> groups = new LinkedHashMap<>();
		for (ArticleFacts article : ranked) {
			EventId event = eventOf.get(article.id());
			if (event != null) {
				groups.computeIfAbsent(event, ignored -> new ArrayList<>()).add(article.id());
			}
		}
		return groups;
	}

	/**
	 * @param ranked 순위대로 된 구독 후보
	 * @param eventOf 기사 → 사건 — 배정이 없는 기사는 빠져 있다
	 * @param representatives 사건 → 그 사건 후보 가운데 대표 기사 — 없으면 그 사건의 가장 앞 순위 후보를 쓴다
	 * @param taken 큰 슬롯이 쓴 사건
	 * @return 순위대로, 사건마다 하나
	 */
	public static List<ArticleFacts> apply(List<ArticleFacts> ranked, Map<ArticleId, EventId> eventOf,
			Map<EventId, ArticleId> representatives, Set<EventId> taken) {
		Objects.requireNonNull(ranked, "ranked");
		Objects.requireNonNull(eventOf, "eventOf");
		Objects.requireNonNull(representatives, "representatives");
		Objects.requireNonNull(taken, "taken");
		Map<ArticleId, ArticleFacts> byId = new HashMap<>();
		ranked.forEach(article -> byId.put(article.id(), article));
		Set<EventId> placed = new HashSet<>();
		List<ArticleFacts> kept = new ArrayList<>();
		for (ArticleFacts article : ranked) {
			EventId event = eventOf.get(article.id());
			if (event == null) {
				kept.add(article);
				continue;
			}
			if (taken.contains(event) || !placed.add(event)) {
				continue;
			}
			ArticleId representative = representatives.get(event);
			ArticleFacts chosen = (representative == null) ? null : byId.get(representative);
			kept.add((chosen == null) ? article : chosen);
		}
		return List.copyOf(kept);
	}

}
