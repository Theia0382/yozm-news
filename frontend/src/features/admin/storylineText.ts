import type { Schemas } from "@/features/admin/adminClient";
import { formatProbability, formatSeconds } from "@/features/admin/metrics";

// 스토리라인 화면들의 기준 · 관계 문구 — 목록과 상세가 같은 문구를 쓰게 한 곳에 둔다(docs/overview.md §8).
// 화면에서는 스토리라인을 "사건 흐름"(메뉴 · 화면 제목) · "흐름"(본문), 큰 판정에 든 것을 "큰 화제", 큰 판정과 그 기준을
// "큰 화제 판정" · "큰 화제 기준"이라 부른다 — 개발 이름은 스토리라인 · 큰 판정 그대로다.

type LinkingCriteria = Schemas["LinkingCriteriaResponse"];
type RelationCriteria = Schemas["RelationCriteriaResponse"];
type RelationKind = Schemas["StorylineRelationResponse"]["kind"];

/** 잇기 기준 — 시간 창 · 띠(아래 ~ 위 기준) · 확률 기준. 띠가 비면 기준 점수 하나 */
export function linkingCriteriaText(criteria: LinkingCriteria): string {
  const window = `창 ${formatSeconds(criteria.windowSeconds)}`;
  if (criteria.lower === criteria.upper || criteria.probability === null) {
    return `${window} · 기준 점수 ${formatProbability(criteria.upper)}`;
  }
  return (
    `${window} · 띠 ${formatProbability(criteria.lower)} ~ ${formatProbability(criteria.upper)} · ` +
    `확률 ${formatProbability(criteria.probability)}`
  );
}

/** 관계 기준 — 확률 · 한 사안 · 사건 수 하한 · 후보 점수 · 시간 창 */
export function relationCriteriaText(criteria: RelationCriteria): string {
  return (
    `확률 ${formatProbability(criteria.probability)} · 한 사안 ${formatProbability(criteria.oneMatter)} · ` +
    `사건 수 ${criteria.minEvents} 이상 · 후보 점수 ${formatProbability(criteria.candidateScore)} · ` +
    `창 ${formatSeconds(criteria.windowSeconds)} · 합치기 확률 ${formatProbability(criteria.mergeProbability)}`
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
