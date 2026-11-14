import type { Schemas } from "@/features/admin/adminClient";

// 사건 · 스토리라인 · 큰 스토리라인 화면 시험의 응답 조각 — 서버 응답 모양(api/openapi.json)을 따른다.

export const ARTICLE: Schemas["ArticleSummaryResponse"] = {
  title: "국회 본회의 통과",
  sources: ["한겨레", "경향신문"],
};

export function eventRow(
  id: number,
  changes: Partial<Schemas["EventRowResponse"]> = {},
): Schemas["EventRowResponse"] {
  return {
    id,
    status: "OPEN",
    articleCount: 5,
    firstArticleAt: "2026-11-05T00:00:00Z",
    lastArticleAt: "2026-11-06T00:00:00Z",
    periodArticles: 3,
    periodSources: 2,
    importance: 3.5,
    representativeId: 41,
    representative: ARTICLE,
    ...changes,
  };
}

export function bigRow(
  id: number,
  changes: Partial<Schemas["BigStorylineRowResponse"]> = {},
): Schemas["BigStorylineRowResponse"] {
  return {
    id,
    state: "ACTIVE",
    eventCount: 2,
    articleCount: 12,
    firstArticleAt: "2026-11-01T00:00:00Z",
    lastArticleAt: "2026-11-06T00:00:00Z",
    intensity: 17.5,
    periodArticles: 3,
    periodSources: 4,
    importance: 3.75,
    representativeArticleId: 41,
    representative: ARTICLE,
    ...changes,
  };
}

export function storylineRow(
  id: number,
  changes: Partial<Schemas["StorylineRowResponse"]> = {},
): Schemas["StorylineRowResponse"] {
  return {
    id,
    state: "ACTIVE",
    parentId: null,
    eventCount: 3,
    articleCount: 12,
    firstArticleAt: "2026-11-01T00:00:00Z",
    lastArticleAt: "2026-11-06T00:00:00Z",
    intensity: 18.75,
    periodArticles: 4,
    periodSources: 3,
    importance: 3.75,
    big: true,
    representativeArticleId: 41,
    representative: ARTICLE,
    ...changes,
  };
}

/** 스토리라인 목록 응답 — 소속 없는 사건 · 지금 기준을 함께 싣는다 */
export function storylineList(
  storylines: Schemas["StorylineRowResponse"][],
  next: Schemas["StorylineCursorResponse"] | null = null,
): Schemas["StorylineListResponse"] {
  return {
    storylines,
    next,
    unlinked: { events: 2, articles: 5 },
    linkingCriteria: { windowSeconds: 259200, threshold: 0.62 },
    relationCriteria: {
      probability: 0.6,
      oneMatter: 0.2,
      minEvents: 3,
      candidateScore: 0.88,
      windowSeconds: 1209600,
    },
    maxDays: 31,
  };
}
