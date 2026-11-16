package net.yozmnews.article.domain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.article.ArticleEntryView;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;

/**
 * 헤드라인의 출처 이름 엮기 — 정본. docs/specs/article.md §5 · article-R17.
 */
public final class HeadlineSources {

	private HeadlineSources() {
	}

	/**
	 * 엔트리 순서로 그 피드의 출처 이름을 모은다 — 같은 이름은 처음 한 번, 이름이 없거나 이름 조회에 없는 피드는 뺀다.
	 * @param entries 기사의 엔트리 — 받은 시각 순(같으면 피드 ID 순)
	 * @param labels 피드 → 출처 이름 · 섹션
	 */
	public static List<String> names(List<ArticleEntryView> entries, Map<FeedId, FeedLabel> labels) {
		Set<String> names = new LinkedHashSet<>();
		for (ArticleEntryView entry : entries) {
			FeedLabel label = labels.get(entry.feedId());
			if (label != null && label.sourceName() != null) {
				names.add(label.sourceName());
			}
		}
		return List.copyOf(names);
	}

	/** 엔트리들이 가리키는 피드 — 처음 나온 순서, 한 번씩 */
	public static Set<FeedId> feeds(Collection<List<ArticleEntryView>> entries) {
		Set<FeedId> feeds = new LinkedHashSet<>();
		entries.forEach(list -> list.forEach(entry -> feeds.add(entry.feedId())));
		return feeds;
	}

}
