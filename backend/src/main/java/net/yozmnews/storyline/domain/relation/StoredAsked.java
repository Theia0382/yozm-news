package net.yozmnews.storyline.domain.relation;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.storyline.RelationOutcome;
import org.jspecify.annotations.Nullable;

/**
 * 읽은 물은 기록 하나 — 쌍마다 마지막으로 물은 결말 · docs/specs/storyline.md §4 · storyline-R30.
 *
 * @param lowId 쌍의 작은 쪽 스토리라인 ID
 * @param highId 쌍의 큰 쪽 스토리라인 ID
 * @param smallerEvents 물었을 때 작은 쪽의 구성 사건 수
 * @param judgementId 판정 원본 ID — 원본이 남지 않은 결말(물을 것 없음 · 입력 거부)이면 null
 * @param criteriaId 그때의 관계 기준 이력 ID
 */
public record StoredAsked(long lowId, long highId, int smallerEvents, RelationOutcome outcome, @Nullable Long judgementId,
		long criteriaId, Instant askedAt) {

	public StoredAsked {
		Objects.requireNonNull(outcome, "outcome");
		Objects.requireNonNull(askedAt, "askedAt");
	}

}
