package net.yozmnews.evaluation.domain.backfill;

import java.math.BigDecimal;
import java.util.Objects;

import net.yozmnews.evaluation.TagSetId;

/**
 * 채움의 버전 하나에 채울 저장 기준 — 요청 때 그 버전의 지금 값으로 고정한다 · docs/specs/evaluation.md §4
 *
 * @param evaluations 요청 때 센 그 버전의 대상 평가 수
 */
public record BackfillVersionPlan(TagSetId version, BigDecimal topicStoreThreshold, BigDecimal regionStoreThreshold,
		long evaluations) {

	public BackfillVersionPlan {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(topicStoreThreshold, "topicStoreThreshold");
		Objects.requireNonNull(regionStoreThreshold, "regionStoreThreshold");
	}

}
