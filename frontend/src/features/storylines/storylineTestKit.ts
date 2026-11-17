// 독자 스토리라인 화면 시험의 응답 조각 — 목록 · 상세 시험이 함께 쓴다.

import type {
  NewsArticle,
  StorylineCard,
  StorylinePage,
  StorylineTimeline,
  TimelineEvent,
} from "@/features/storylines/storylineQueries";

export const T = "2026-11-05T00:00:00Z";

export const ARTICLE: NewsArticle = {
  title: "국회 본회의 통과",
  sources: ["한겨레", "경향신문"],
  publishedAt: T,
  url: "https://news.example/41",
};

export function card(
  id: number,
  overrides: Partial<StorylineCard> = {},
): StorylineCard {
  return {
    id,
    title: ARTICLE,
    state: "ACTIVE",
    eventCount: 3,
    articleCount: 12,
    firstArticleAt: "2026-11-01T00:00:00Z",
    lastArticleAt: T,
    parentId: null,
    period: { articles: 4, big: false },
    ...overrides,
  };
}

export function page(
  storylines: StorylineCard[],
  overrides: Partial<StorylinePage> = {},
): StorylinePage {
  return {
    window: "WEEK",
    from: "2026-10-29T00:00:00Z",
    until: T,
    storylines,
    next: null,
    ...overrides,
  };
}

export function event(
  eventId: number,
  overrides: Partial<TimelineEvent> = {},
): TimelineEvent {
  return {
    eventId,
    direct: true,
    articleCount: 2,
    firstArticleAt: T,
    lastArticleAt: T,
    representative: ARTICLE,
    receivedOn: null,
    ...overrides,
  };
}

export function timeline(
  overrides: Partial<StorylineTimeline> = {},
): StorylineTimeline {
  return {
    storyline: card(7, { period: null }),
    parent: null,
    children: [],
    preceding: [],
    following: [],
    events: [event(70)],
    ...overrides,
  };
}
