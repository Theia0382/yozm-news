package net.yozmnews.event.domain.feature;

import java.util.Collection;

import net.yozmnews.article.ArticleId;

/**
 * 기사 표현 저장소 — 표현 행은 만든 뒤 바꾸지 않는다 · docs/specs/event.md §4.
 */
public interface MatchFeaturesRepository {

	/**
	 * 없으면 쓴다 — (기사, 모델 버전)이 이미 있으면 그대로다(event-R4).
	 * @return 썼으면 true
	 */
	boolean insertIfAbsent(MatchFeatures features);

	/** 기사들의 표현을 모든 모델 버전에 걸쳐 지운다 — 지운 기사 따르기(event-R8) */
	void deleteByArticles(Collection<ArticleId> articleIds);

}
