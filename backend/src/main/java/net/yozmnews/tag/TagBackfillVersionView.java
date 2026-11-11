package net.yozmnews.tag;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 빠진 태그 채움의 버전 하나 — 채울 저장 기준과 결과 · docs/specs/tag.md §3
 *
 * @param number 버전 번호
 * @param topicStoreThreshold 주제를 채울 저장 기준 — 요청 때 그 버전의 지금 값
 * @param regionStoreThreshold 지역을 채울 저장 기준 — 요청 때 그 버전의 지금 값
 * @param evaluations 요청 때 센 대상 평가 수
 * @param processed 처리한 평가 수 — 건너뛴 평가 포함
 * @param tagsAdded 더한 태그 행 수
 * @param skippedOtherEvaluator 평가기가 달라 건너뛴 평가 수
 * @param skippedUnreadable 원본 응답을 읽을 수 없거나 체계와 어긋나 건너뛴 평가 수
 */
public record TagBackfillVersionView(TagSetId version, int number, BigDecimal topicStoreThreshold,
		BigDecimal regionStoreThreshold, long evaluations, long processed, long tagsAdded, long skippedOtherEvaluator,
		long skippedUnreadable) {

	public TagBackfillVersionView {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(topicStoreThreshold, "topicStoreThreshold");
		Objects.requireNonNull(regionStoreThreshold, "regionStoreThreshold");
	}

}
