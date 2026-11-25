import { useQuery } from "@tanstack/react-query";
import { useApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type StorylineCard = components["schemas"]["StorylineCardResponse"];
export type StorylineTimeline =
  components["schemas"]["StorylineTimelineResponse"];
export type StorylineRef = components["schemas"]["StorylineRefResponse"];
export type TimelineEvent = components["schemas"]["TimelineEventResponse"];
export type NewsArticle = components["schemas"]["NewsArticleResponse"];

/** 독자 화면의 공용 스토리라인(타임라인) 조회 키의 접두 */
export const STORYLINES_QUERY_KEY = ["storylines"] as const;

/** 사용자 스토리라인 요청이 실패했다 — 화면이 상태 코드로 가른다 */
export class StorylineRequestError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`흐름을 읽지 못했습니다 (HTTP ${status})`);
    this.status = status;
  }
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
