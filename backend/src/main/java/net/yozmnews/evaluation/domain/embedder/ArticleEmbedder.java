package net.yozmnews.evaluation.domain.embedder;

import java.util.List;

import net.yozmnews.evaluation.EmbedderInfo;
import net.yozmnews.evaluation.EmbeddingResult;

/**
 * 기사 임베딩 포트 — 텍스트를 벡터로 바꾸는 외부 의존(임베딩 모델 · 외부 API) · docs/specs/evaluation.md §4
 */
public interface ArticleEmbedder {

	/** 이름 · 모델 버전 · 차원을 낸다 — docs/specs/evaluation.md §4 */
	EmbedderInfo info();

	/** 텍스트마다 벡터 하나를 낸다 — 예상한 실패는 결과 값이다 · docs/specs/evaluation.md §4 */
	EmbeddingResult embed(List<String> texts);

}
