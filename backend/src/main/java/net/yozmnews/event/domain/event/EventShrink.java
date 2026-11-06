package net.yozmnews.event.domain.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.assignment.CentroidUpdate;
import net.yozmnews.event.domain.feature.MatchFeatures;

/**
 * 구성 기사를 지운 뒤 사건의 새 값 — 정본은 {@link #of} · docs/specs/event.md §5.
 */
public sealed interface EventShrink {

	/** 남는 기사가 없다 — 사건을 지운다 */
	record Emptied() implements EventShrink {
	}

	/**
	 * 기사가 남는다.
	 * @param centroid 새 중심 — 표현이 있는 지운 기사의 벡터만 뺀 값
	 * @param articleCount 남는 기사 수
	 * @param removedTerms 지운 기사들의 핵심 말 — 기사마다 이어 붙여, 여러 기사에 있는 말은 그 수만큼 나온다
	 */
	record Shrunk(float[] centroid, int articleCount, List<String> removedTerms) implements EventShrink {

		public Shrunk {
			centroid = centroid.clone();
			removedTerms = List.copyOf(removedTerms);
		}

		@Override
		public float[] centroid() {
			return this.centroid.clone();
		}

	}

	/**
	 * 지운 구성 기사를 사건에서 뺀다. 지운 기사마다 기사 수를 하나 줄이고, 그 사건 모델 버전의 표현이 있으면 중심에서 그 벡터를 빼고 핵심 말을
	 * 줄인다. 표현이 없으면(보존 기간이 지나 지움) 중심 · 핵심 말은 그대로다 — 누적 갱신의 한계다.
	 * @param removed 이 사건에서 지운 기사 — 기사 ID 순
	 * @param representations 그 기사들의 그 사건 모델 버전 표현 — 없는 기사는 빠져 있다
	 */
	static EventShrink of(LockedEvent event, List<ArticleId> removed, Map<ArticleId, MatchFeatures> representations) {
		int remaining = event.articleCount() - removed.size();
		if (remaining < 1) {
			return new Emptied();
		}
		float[] centroid = event.centroid();
		int count = event.articleCount();
		List<String> removedTerms = new ArrayList<>();
		for (ArticleId article : removed) {
			MatchFeatures representation = representations.get(article);
			if (representation != null) {
				centroid = CentroidUpdate.remove(centroid, count, representation.embedding().values());
				removedTerms.addAll(representation.entities());
			}
			count--;
		}
		return new Shrunk(centroid, count, removedTerms);
	}

}
