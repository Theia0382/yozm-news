import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { useApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type StorylinePage = components["schemas"]["StorylinePageResponse"];
export type StorylineCard = components["schemas"]["StorylineCardResponse"];
export type StorylineTimeline =
  components["schemas"]["StorylineTimelineResponse"];
export type StorylineRef = components["schemas"]["StorylineRefResponse"];
export type TimelineEvent = components["schemas"]["TimelineEventResponse"];
export type NewsArticle = components["schemas"]["NewsArticleResponse"];
export type StorylineWindow = StorylinePage["window"];

/** 독자 화면의 스토리라인 조회 키의 접두 */
export const STORYLINES_QUERY_KEY = ["storylines"] as const;

/** 스토리라인 요청이 실패했다 — 화면이 상태 코드로 가른다 */
export class StorylineRequestError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`스토리라인을 읽지 못했습니다 (HTTP ${status})`);
    this.status = status;
  }
}

type PageParam = { until: string; at: string; id: number } | null;

/**
 * 목록 기간의 스토리라인 — 다음 쪽은 첫 응답의 until 과 커서를 함께 넘긴다(reader-R2). until 을 빠뜨리면 서버가 지금을 기간 끝으로 써서
 * 쪽 사이에 기간이 미끄러진다.
 */
export function useStorylinePage(window: StorylineWindow) {
  const client = useApiClient();
  return useInfiniteQuery({
    queryKey: [...STORYLINES_QUERY_KEY, "page", window],
    initialPageParam: null as PageParam,
    queryFn: async ({ pageParam }): Promise<StorylinePage> => {
      const { data, response } = await client.GET("/api/storylines", {
        params: {
          query: {
            window,
            ...(pageParam
              ? {
                  until: pageParam.until,
                  afterLastArticleAt: pageParam.at,
                  afterId: pageParam.id,
                }
              : {}),
          },
        },
      });
      if (!response.ok || !data) {
        throw new StorylineRequestError(response.status);
      }
      return data;
    },
    getNextPageParam: (page): PageParam | undefined =>
      page.next
        ? { until: page.until, at: page.next.lastArticleAt, id: page.next.id }
        : undefined,
  });
}

/** 스토리라인 하나의 타임라인 — 합쳐진 ID 면 남는 스토리라인이 온다 */
export function useStorylineTimeline(id: number) {
  const client = useApiClient();
  return useQuery({
    queryKey: [...STORYLINES_QUERY_KEY, "timeline", id],
    queryFn: async (): Promise<StorylineTimeline> => {
      const { data, response } = await client.GET("/api/storylines/{id}", {
        params: { path: { id } },
      });
      if (!response.ok || !data) {
        throw new StorylineRequestError(response.status);
      }
      return data;
    },
  });
}
