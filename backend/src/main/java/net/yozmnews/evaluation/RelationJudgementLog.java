package net.yozmnews.evaluation;

import java.util.Collection;
import java.util.Map;

/**
 * 관계 판정 원본 조회 — 운영자 화면이 관계 · 물은 기록이 가리키는 판정 원본을 ID 로 찾는다 · docs/specs/evaluation.md §3 ·
 * evaluation-R50.
 * <p>
 * 모델을 부르지 않는다 — 저장된 원본 응답을 그 원본을 남긴 평가기의 형식으로 다시 읽어 답마다의 확률을 낸다. 트랜잭션 · 역할 애너테이션이
 * 없다(두 역할 공통). 원본 응답 · 서술은 내지 않는다.
 */
public interface RelationJudgementLog {

	/** 한 번에 찾는 원본 수의 상한 — 정본. 측정 전 목표 */
	int MAX_IDS = 500;

	/**
	 * 주어진 ID 의 판정 원본.
	 * @return 원본 ID → 원본 — 없는(지운) ID 는 결과에 없다
	 * @throws IllegalArgumentException ID 수가 {@link #MAX_IDS} 를 넘을 때
	 */
	Map<RelationJudgementId, RelationJudgementView> findAll(Collection<RelationJudgementId> ids);

}
