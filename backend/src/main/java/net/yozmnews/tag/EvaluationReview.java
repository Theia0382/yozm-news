package net.yozmnews.tag;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * 평가 검수 — 운영자 화면이 평가 결과를 한 건씩 보고 표본을 검수한다 · docs/specs/tag.md §3.
 * <p>
 * 구현은 {@code application} 의 {@code EvaluationReviewService} 다. 세 연산 모두 읽기 전용 {@code REPEATABLE READ} 트랜잭션 하나에서
 * 읽고, 판정은 그 평가 버전의 지금 태그 기준 · 저장 기준으로 조회 때 한다(tag-R15). 기사 내용 · 출처 이름은 싣지 않는다.
 */
public interface EvaluationReview {

	/** 목록 한 번에 받는 평가 수의 상한 — 정본이다 */
	int MAX_PAGE_SIZE = 50;

	/** 표본 크기의 기본값 — 정본이다 */
	int DEFAULT_SAMPLE_SIZE = 100;

	/** 표본 크기의 상한 — 정본이다 */
	int MAX_SAMPLE_SIZE = 200;

	/** 씨앗의 상한 — 화면(JavaScript 수)이 정확히 나타내는 2^53 − 1. 정본이다 */
	long MAX_SEED = (1L << 53) - 1;

	/**
	 * 평가 목록 — 그 버전의 평가 가운데 평가 시각이 기간 안인 것을 평가 시각 · ID 내림차순으로, {@code after} 다음부터 거르기에 맞는 것
	 * {@code limit} 건(tag-R33).
	 * @param after 이어 받을 자리 — 처음이면 null
	 */
	EvaluationListResult list(TagSetId version, EvaluationPeriod period, EvaluationFilter filter,
			@Nullable EvaluationCursor after, int limit);

	/** 표본 — 같은 모집단에서 씨앗으로 고른 {@code size} 건(tag-R34) */
	EvaluationSampleResult sample(TagSetId version, EvaluationPeriod period, long seed, int size);

	/**
	 * 평가 하나의 상세.
	 * @return 없는 평가면 빈 값
	 */
	Optional<EvaluationDetailView> detail(EvaluationId id);

}
