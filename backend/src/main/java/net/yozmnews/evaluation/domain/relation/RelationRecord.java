package net.yozmnews.evaluation.domain.relation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.domain.evaluator.Relation;

/**
 * 저장할 관계 판정 원본 하나 — docs/specs/evaluation.md §4 · evaluation-R49.
 * <p>
 * 제목 · 요약문 텍스트는 남기지 않는다 — 요청에 실은 서술 기사의 ID 와 요약문을 실었는가만 남긴다.
 *
 * @param firstFlowId 요청의 first 흐름
 * @param secondFlowId 요청의 second 흐름
 * @param first first 에서 요청에 실은 서술 — 요청 순서
 * @param second second 에서 요청에 실은 서술 — 요청 순서
 * @param wordingVersion 문구의 판
 * @param rawResponse 평가기의 원본 응답 — JSON 텍스트
 */
public record RelationRecord(long firstFlowId, long secondFlowId, List<Described> first, List<Described> second,
		String evaluator, String modelVersion, String wordingVersion, int inputTokens, String rawResponse,
		Instant judgedAt) {

	/**
	 * @throws IllegalArgumentException 흐름 ID 가 1 미만이거나 같거나, 한쪽의 서술이 없거나, 이름 · 버전 · 판이 공백뿐이거나, 토큰 수가
	 * 음수일 때
	 */
	public RelationRecord {
		if (firstFlowId < 1 || secondFlowId < 1) {
			throw new IllegalArgumentException("흐름 ID 는 1 이상이다: " + firstFlowId + ", " + secondFlowId);
		}
		if (firstFlowId == secondFlowId) {
			throw new IllegalArgumentException("두 흐름 ID 가 같다: " + firstFlowId);
		}
		first = List.copyOf(first);
		second = List.copyOf(second);
		if (first.isEmpty() || second.isEmpty()) {
			throw new IllegalArgumentException("실은 서술이 없는 흐름이 있다");
		}
		Objects.requireNonNull(evaluator, "evaluator");
		Objects.requireNonNull(modelVersion, "modelVersion");
		Objects.requireNonNull(wordingVersion, "wordingVersion");
		Objects.requireNonNull(rawResponse, "rawResponse");
		Objects.requireNonNull(judgedAt, "judgedAt");
		if (evaluator.isBlank() || modelVersion.isBlank() || wordingVersion.isBlank()) {
			throw new IllegalArgumentException("평가기 이름 · 모델 버전 · 문구 판이 비었다");
		}
		if (inputTokens < 0) {
			throw new IllegalArgumentException("입력 토큰 수가 음수다: " + inputTokens);
		}
	}

	/** 판정 결과를 저장할 모양으로 */
	public static RelationRecord of(long firstFlowId, long secondFlowId, List<Described> first, List<Described> second,
			Relation relation, String wordingVersion, Instant judgedAt) {
		return new RelationRecord(firstFlowId, secondFlowId, first, second, relation.evaluator(),
				relation.modelVersion(), wordingVersion, relation.inputTokens(), relation.rawResponse(), judgedAt);
	}

	/**
	 * 요청에 실은 서술 하나.
	 *
	 * @param articleId 서술 기사
	 * @param summarized 요약문을 실었는가
	 */
	public record Described(ArticleId articleId, boolean summarized) {

		public Described {
			Objects.requireNonNull(articleId, "articleId");
		}

	}

}
