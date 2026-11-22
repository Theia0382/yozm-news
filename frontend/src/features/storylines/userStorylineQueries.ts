import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { useApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";
import { StorylineRequestError } from "@/features/storylines/storylineQueries";

export type UserStorylineCard = components["schemas"]["CardResponse"];
export type UserStorylineDetail = components["schemas"]["DetailResponse"];
export type UserStorylineTop = components["schemas"]["TopResponse"];
export type UserStorylineUsage = components["schemas"]["UsageResponse"];
export type UserStorylineItems = components["schemas"]["ItemsResponse"];
export type UserStorylineItem = components["schemas"]["ItemResponse"];

/** 정리된 사용자 스토리라인 조회 키의 접두 */
export const USER_STORYLINES_QUERY_KEY = ["user-storylines"] as const;

/** 정리된 내 흐름 목록 — 마지막으로 정리한 순(docs/specs/reader.md) */
export function useUserStorylines() {
  const client = useApiClient();
  return useQuery({
    queryKey: [...USER_STORYLINES_QUERY_KEY, "list"],
    queryFn: async (): Promise<UserStorylineCard[]> => {
      const { data, response } = await client.GET("/api/user-storylines");
      if (!response.ok || !data) {
        throw new StorylineRequestError(response.status);
      }
      return data.userStorylines;
    },
  });
}

/** 정리된 흐름 하나 — 흐름 전체의 글 · 갈래마다의 글 · 사용량 */
export function useUserStoryline(id: number) {
  const client = useApiClient();
  return useQuery({
    queryKey: [...USER_STORYLINES_QUERY_KEY, "one", id],
    queryFn: async (): Promise<UserStorylineDetail> => {
      const { data, response } = await client.GET("/api/user-storylines/{id}", {
        params: { path: { id } },
      });
      if (!response.ok || !data) {
        throw new StorylineRequestError(response.status);
      }
      return data;
    },
  });
}

type ItemsParam = { beforeDate: string; beforePosition: number } | null;

/** 그 흐름에 묶인 지난 다이제스트 항목 — 다음 쪽은 앞 응답의 커서 두 값을 그대로 넘긴다 */
export function useUserStorylineItems(id: number) {
  const client = useApiClient();
  return useInfiniteQuery({
    queryKey: [...USER_STORYLINES_QUERY_KEY, "items", id],
    initialPageParam: null as ItemsParam,
    queryFn: async ({ pageParam }): Promise<UserStorylineItems> => {
      const { data, response } = await client.GET(
        "/api/user-storylines/{id}/items",
        { params: { path: { id }, query: pageParam ?? {} } },
      );
      if (!response.ok || !data) {
        throw new StorylineRequestError(response.status);
      }
      return data;
    },
    getNextPageParam: (page): ItemsParam | undefined => page.next ?? undefined,
  });
}
