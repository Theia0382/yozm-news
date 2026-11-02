package net.yozmnews.evaluation;

/**
 * 공유 평가의 운영 — 대기 · 실패 건수 · docs/specs/evaluation.md §3. 화면은 {@code admin} 이 이것을 불러 낸다.
 */
public interface EvaluationAdministration {

	/** 지금의 대기 · 실패 건수 — 질의 하나로 센다 */
	EvaluationQueueCounts queueCounts();

}
