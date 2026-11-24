package net.yozmnews.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 기사 표현 한 행의 핵심 말 — 벡터는 싣지 않는다 · docs/specs/event.md §3 · event-R2 · event-R4.
 *
 * @param modelVersion 표현을 만든 임베딩 모델 버전
 * @param terms 핵심 말 — 표현 텍스트에서 처음 나온 순서, 비어 있을 수 있다
 * @param createdAt 표현을 만든 시각
 */
public record ArticleKeyTerms(String modelVersion, List<String> terms, Instant createdAt) {

	public ArticleKeyTerms {
		Objects.requireNonNull(modelVersion, "modelVersion");
		terms = List.copyOf(terms);
		Objects.requireNonNull(createdAt, "createdAt");
	}

}
