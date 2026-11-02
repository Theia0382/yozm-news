package net.yozmnews.source.application;

import java.util.Collection;
import java.util.Map;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import net.yozmnews.source.FeedLabels;
import org.springframework.stereotype.Service;

/**
 * 공개 조회 {@link FeedLabels} 의 구현 — docs/specs/source.md §4. 이름 · 섹션의 해석은 운영 목록과 같은 {@link SourceCatalog} 가 한다.
 */
@Service
class FeedLabelService implements FeedLabels {

	private final SourceCatalog catalog;

	FeedLabelService(SourceCatalog catalog) {
		this.catalog = catalog;
	}

	@Override
	public Map<FeedId, FeedLabel> labels(Collection<FeedId> feeds) {
		return this.catalog.labels(feeds);
	}

}
