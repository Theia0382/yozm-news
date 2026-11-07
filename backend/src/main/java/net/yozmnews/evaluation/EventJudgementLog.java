package net.yozmnews.evaluation;

import org.jspecify.annotations.Nullable;

/**
 * 사건 소속 판정 원본의 조회 — 운영자 화면이 기간의 판정을 훑는다 · docs/specs/evaluation.md §3 · evaluation-R39.
 * <p>
 * 트랜잭션을 열지 않는다 — 질의 하나다.
 */
public interface EventJudgementLog {

	/** 판정 원본 목록 한 쪽의 상한 — 정본. 측정 전 목표 */
	int MAX_PAGE_SIZE = 100;

	/**
	 * 판정 시각이 기간 안인 판정 원본 — (판정 시각, ID) 내림차순.
	 * @param after 앞 쪽의 {@link EventJudgementPage#next()} — 첫 쪽이면 null
	 * @throws IllegalArgumentException {@code size} 가 1 ~ {@link #MAX_PAGE_SIZE} 밖일 때
	 */
	EventJudgementPage list(EvaluationPeriod period, @Nullable EventJudgementCursor after, int size);

}
