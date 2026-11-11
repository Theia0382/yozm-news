package net.yozmnews.tag;

import java.util.Set;

/**
 * 주제별 보도량 — 첫 평가 시각이 기간 안인 기사를 활성 버전의 주제마다 센다 · docs/specs/tag.md §3.
 * <p>
 * 기사마다 최신 버전 평가를 그 버전의 체계 · 지금 태그 기준 · 저장 기준으로 판정하고(low_confidence 의 주제 포함), 그 버전 → 활성 버전
 * 태그 키 계보로 주제 키를 활성 키에 잇는다. 주제 · 대분류마다 기사는 한 번 센다 — 대분류의 수는 하위 중분류 어느 것이든 붙은 기사 수다.
 * 사용자를 모른다. 구현은 {@code application} 의 {@code TopicCoverageService} 다.
 */
public interface TopicCoverage {

	/**
	 * 기간의 보도량을 센다 — 읽기 전용 {@code REPEATABLE READ} 트랜잭션 하나(한 스냅숏)에서 센다.
	 * @param topics 고른 주제 — 활성 버전의 대분류 · 중분류(전체 키) 키. 빈 집합이면 모든 주제, 대분류 키는 그 하위 중분류 전부로 펼친다
	 * @return 고른 키 가운데 활성 버전에 없는 것이 있으면 {@link TopicCoverageResult.UnknownTopics} — 아무것도 세지 않는다
	 */
	TopicCoverageResult count(EvaluationPeriod period, Set<String> topics);

}
