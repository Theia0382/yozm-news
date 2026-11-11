package net.yozmnews.evaluation;

import java.util.List;

/**
 * 텍스트 임베딩 — 텍스트마다 벡터 하나를 임베더로 만든다 · docs/specs/evaluation.md §3 · evaluation-R45.
 * <p>
 * 임베딩은 외부 호출이라 작업 역할의 작업에서, DB 트랜잭션 밖에서 부른다. 결과를 기록하지 않는다 — 벡터와 그 모델 버전은 부르는 쪽의
 * 데이터다.
 */
public interface TextEmbedding {

	/**
	 * 지금 임베더의 이름 · 모델 버전 · 차원.
	 * @throws IllegalStateException 임베더가 없는 프로세스에서 불렀을 때 — 부르는 쪽 결함이다
	 */
	EmbedderInfo info();

	/**
	 * 텍스트마다 벡터 하나를 넘긴 순서로 낸다. 성공이면 벡터 수가 텍스트 수와 같고 모든 벡터의 차원이 {@link #info()} 의 차원이다.
	 * 예상한 실패는 결과 값이다. 빈 목록이면 임베더를 부르지 않고 빈 성공을 낸다.
	 * @throws IllegalStateException 트랜잭션 안에서 불렀거나, 임베더가 없는 프로세스에서 불렀거나, 임베더 결과의 수 · 차원이 어긋났을 때
	 */
	EmbeddingResult embed(List<String> texts);

}
