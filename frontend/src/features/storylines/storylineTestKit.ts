// 독자 스토리라인 화면 시험의 응답 조각 — 목록 · 상세 · 구독 시험이 함께 쓴다.

import type {
  NewsArticle,
  StorylineCard,
  StorylinePage,
  StorylineTimeline,
  TimelineEvent,
} from "@/features/storylines/storylineQueries";
import type { StorylineSubscriptions } from "@/features/storylines/subscriptionQueries";
import { type Handler, json } from "@/test/fakeServer";

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

/** 내 구독 응답 — 구독 시각은 모두 T, 상태는 진행 */
export function subscriptions(
  ids: number[],
  limit = 100,
): StorylineSubscriptions {
  return {
    limit,
    subscriptions: ids.map((storylineId) => ({
      storylineId,
      subscribedAt: T,
      state: "ACTIVE",
    })),
  };
}

/**
 * 구독 경로 셋의 가짜 — 구독한 ID 집합을 들고, `ids` 의 스토리라인마다 구독하면 더하고(SUBSCRIBED) 해제하면 뺀다(UNSUBSCRIBED ·
 * NOT_SUBSCRIBED). 내 구독은 그 집합을 낸다.
 */
export function subscriptionHandlers(
  initial: number[],
  ids: number[],
): Record<string, Handler> {
  const subscribed = new Set(initial);
  const handlers: Record<string, Handler> = {
    "GET /api/storyline-subscriptions": () =>
      json(subscriptions([...subscribed])),
  };
  for (const id of ids) {
    handlers[`PUT /api/storyline-subscriptions/${id}`] = () => {
      subscribed.add(id);
      return json({ result: "SUBSCRIBED", storylineId: id, limit: null });
    };
    handlers[`DELETE /api/storyline-subscriptions/${id}`] = () =>
      json({
        result: subscribed.delete(id) ? "UNSUBSCRIBED" : "NOT_SUBSCRIBED",
      });
  }
  return handlers;
}
