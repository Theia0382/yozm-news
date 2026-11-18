import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { StorylineRequestError } from "@/features/storylines/storylineQueries";
import { useApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type StorylineSubscriptions =
  components["schemas"]["StorylineSubscriptionsResponse"];
export type SubscribeResult =
  components["schemas"]["StorylineSubscribeResponse"];
export type UnsubscribeResult =
  components["schemas"]["StorylineUnsubscribeResponse"];

/** 스토리라인 구독 조회 키의 접두 — 구독 조회는 모두 이 아래에 두어 구독 · 해제 뒤 한 번에 다시 읽힌다 */
export const STORYLINE_SUBSCRIPTIONS_QUERY_KEY = [
  "storyline-subscriptions",
] as const;

/**
 * 내 구독 — 스토리라인 ID · 구독 시각 · 지금 상태와 상한(docs/specs/user.md user-R52). 목록 · 타임라인 · 내 구독이 같은 조회를
 * 함께 쓴다.
 */
export function useStorylineSubscriptions() {
  const client = useApiClient();
  return useQuery({
    queryKey: [...STORYLINE_SUBSCRIPTIONS_QUERY_KEY, "list"],
    queryFn: async (): Promise<StorylineSubscriptions> => {
      const { data, response } = await client.GET(
        "/api/storyline-subscriptions",
      );
      if (!response.ok || !data) {
        throw new StorylineRequestError(response.status);
      }
      return data;
    },
  });
}

/** 구독 · 해제 한 번의 결과 */
export type SubscriptionChangeResult =
  | { kind: "subscribe"; result: SubscribeResult }
  | { kind: "unsubscribe"; result: UnsubscribeResult };

/**
 * 구독(subscribe 가 참) · 해제 — 서버가 합쳐진 ID 를 남는 스토리라인으로 풀어 판정한다. 성공하면 접두 키 아래의 구독 조회를 다시 읽는다.
 */
export function useSubscriptionToggle() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      storylineId,
      subscribe,
    }: {
      storylineId: number;
      subscribe: boolean;
    }): Promise<SubscriptionChangeResult> => {
      const params = { params: { path: { storylineId } } };
      if (subscribe) {
        const { data, response } = await client.PUT(
          "/api/storyline-subscriptions/{storylineId}",
          params,
        );
        if (!response.ok || !data) {
          throw new StorylineRequestError(response.status);
        }
        return { kind: "subscribe", result: data };
      }
      const { data, response } = await client.DELETE(
        "/api/storyline-subscriptions/{storylineId}",
        params,
      );
      if (!response.ok || !data) {
        throw new StorylineRequestError(response.status);
      }
      return { kind: "unsubscribe", result: data };
    },
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: STORYLINE_SUBSCRIPTIONS_QUERY_KEY,
      }),
  });
}
