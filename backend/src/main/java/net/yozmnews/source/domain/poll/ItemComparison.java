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
 * 이번 수집의 항목을 직전 성공 수집의 키와 비교한 결과 — docs/specs/source.md §4 · source-R20 · source-R21.
 *
 * @param newArticles 직전 키에 없는 기사 — 문서 순서, 같은 키는 처음 것만
 * @param currentKeys 이번 수집의 키 — 문서 순서, 중복 없음. 다음 비교의 직전 키가 된다
 * @param overflowSuspected 넘침 의심 — source-R21
 */
public record ItemComparison(List<FetchedArticle> newArticles, List<ItemKey> currentKeys, boolean overflowSuspected) {

	public ItemComparison {
		newArticles = List.copyOf(newArticles);
		currentKeys = List.copyOf(currentKeys);
	}

	/**
	 * 직전 키와 이번 기사를 비교한다.
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
