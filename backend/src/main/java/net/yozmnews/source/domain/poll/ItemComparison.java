package net.yozmnews.source.domain.poll;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.yozmnews.source.FetchedArticle;
import org.jspecify.annotations.Nullable;

/**
 * 이번 수집의 항목을 직전 성공 수집의 키와 비교한 결과. source 가 말하는 "새 항목"은 이 피드에서 직전에 받지 않은 항목이다 — 같은
 * 기사인지는 article 이 판정한다.
 *
 * @param newArticles 직전 키에 없는 기사 — 문서 순서, 같은 키는 처음 것만
 * @param currentKeys 이번 수집의 키 — 문서 순서, 중복 없음. 다음 비교의 직전 키가 된다
 * @param overflowSuspected 넘침 의심 — 직전 키가 있고(첫 수집이 아니고) 비어 있지 않고, 이번 키가 있고, 겹치는 키가 하나도 없다.
 * 수집 간격 동안 피드가 싣는 건수보다 많은 기사가 나와 일부가 받기 전에 밀려났을 수 있다
 */
public record ItemComparison(List<FetchedArticle> newArticles, List<ItemKey> currentKeys, boolean overflowSuspected) {

	public ItemComparison {
		newArticles = List.copyOf(newArticles);
		currentKeys = List.copyOf(currentKeys);
	}

	/**
	 * @param previous 직전 성공 수집의 키 — 아직 성공한 수집이 없으면 null
	 * @param articles 이번 수집의 기사 — 문서 순서
	 */
	public static ItemComparison compare(@Nullable List<ItemKey> previous, List<FetchedArticle> articles) {
		Set<ItemKey> seen = (previous != null) ? new HashSet<>(previous) : Collections.emptySet();
		Set<ItemKey> current = new LinkedHashSet<>();
		List<FetchedArticle> fresh = new ArrayList<>();
		for (FetchedArticle article : articles) {
			ItemKey key = ItemKey.of(article);
			if (current.add(key) && !seen.contains(key)) {
				fresh.add(article);
			}
		}
		boolean overflow = previous != null && !previous.isEmpty() && !current.isEmpty()
				&& current.stream().noneMatch(seen::contains);
		return new ItemComparison(fresh, List.copyOf(current), overflow);
	}

}
