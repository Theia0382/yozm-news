package net.yozmnews.event.domain.feature;

import java.util.List;

/**
 * 기사 임베딩 포트 — 텍스트를 벡터로 바꾸는 외부 의존(임베딩 모델 · 외부 API) · docs/specs/event.md §4
 */
public interface ArticleEmbedder {

	/** 이름 · 모델 버전 · 차원을 낸다 — docs/specs/event.md §4 */
	EmbedderInfo info();

	/** 텍스트마다 벡터 하나를 낸다 — docs/specs/event.md §4 */
	List<Embedding> embed(List<String> texts);

}
