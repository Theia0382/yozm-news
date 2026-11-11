package net.yozmnews.tag;

import java.util.Optional;

/**
 * 공유 평가의 운영 — 대기 · 실패 건수와 평가 현황 · docs/specs/tag.md §3. 화면은 {@code admin} 이 이것을 불러 낸다.
 */
public interface EvaluationAdministration {

	/** 지금의 대기 · 실패 건수 — 질의 하나로 센다 */
	EvaluationQueueCounts queueCounts();

	/**
	 * 그 버전의 평가 가운데 평가 시각이 기간 안인 것의 평가 현황 — 읽기 전용 {@code REPEATABLE READ} 트랜잭션 하나(한 스냅숏)에서 센다.
	 * @return 없는 버전이면 빈 값
	 */
	Optional<EvaluationOverview> overview(TagSetId version, EvaluationPeriod period);

}
