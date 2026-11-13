import type { Schemas } from "@/features/admin/adminClient";
import { formatProbability, formatSeconds } from "@/features/admin/metrics";

// 스토리라인 화면들의 기준 · 관계 문구 — 목록과 상세가 같은 문구를 쓰게 한 곳에 둔다.

type LinkingCriteria = Schemas["LinkingCriteriaResponse"];
type RelationCriteria = Schemas["RelationCriteriaResponse"];
type RelationKind = Schemas["StorylineRelationResponse"]["kind"];

/** 잇기 기준 — 시간 창 · 기준 점수 */
export function linkingCriteriaText(criteria: LinkingCriteria): string {
  return `창 ${formatSeconds(criteria.windowSeconds)} · 기준 점수 ${formatProbability(criteria.threshold)}`;
}

/** 관계 기준 — 확률 · 한 사안 · 사건 수 하한 · 후보 점수 · 시간 창 */
export function relationCriteriaText(criteria: RelationCriteria): string {
  return (
    `확률 ${formatProbability(criteria.probability)} · 한 사안 ${formatProbability(criteria.oneMatter)} · ` +
    `사건 수 ${criteria.minEvents} 이상 · 후보 점수 ${formatProbability(criteria.candidateScore)} · ` +
    `창 ${formatSeconds(criteria.windowSeconds)}`
  );
}

/** 관계의 방향 — 보고 있는 스토리라인 기준 */
export function relationDirection(
  kind: RelationKind,
  sourceId: number,
  self: number,
): string {
  switch (kind) {
    case "CONTAINS":
      return sourceId === self ? "포함함" : "포함됨";
    case "FOLLOWS":
      return sourceId === self ? "낳음" : "이어받음";
  }
}
