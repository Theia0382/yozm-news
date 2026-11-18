package net.yozmnews.storyline;

import java.util.List;
import java.util.Objects;

import net.yozmnews.evaluation.RelationJudgementId;

/**
 * 관계 하나와 그 판정 — docs/specs/storyline.md §3 · storyline-R34.
 *
 * @param probability 맺은 판정에서 가장 높았던 답의 확률
 * @param judgement 그 판정 원본 ID — 원본은 먼저 지워졌을 수 있다
 * @param criteria 그 판정에 쓴 관계 기준
 * @param groupedBy 그 고리를 맺은 나란한 흐름 쌍(작은 ID · 큰 ID) — 나란한 흐름으로 판정된 두 스토리라인 위에 상위를 열 때 맺은 고리면
 * 그 두 스토리라인이고, 확률 · 판정 원본도 그 쌍의 나란한 흐름 판정 것이다. 그 고리의 두 끝이 스스로 판정받은 보통 관계면 비어 있다
 */
public record StorylineRelationDetail(StorylineRelationView relation, double probability, RelationJudgementId judgement,
		StorylineRelationCriteria criteria, List<StorylineId> groupedBy) {

	/**
	 * @throws IllegalArgumentException 맺은 쌍이 비지도 둘도 아닐 때
	 */
	public StorylineRelationDetail {
		Objects.requireNonNull(relation, "relation");
		Objects.requireNonNull(judgement, "judgement");
		Objects.requireNonNull(criteria, "criteria");
		groupedBy = List.copyOf(groupedBy);
		if (!groupedBy.isEmpty() && groupedBy.size() != 2) {
			throw new IllegalArgumentException("맺은 쌍은 없거나 스토리라인 둘이다: " + groupedBy);
		}
	}

}
