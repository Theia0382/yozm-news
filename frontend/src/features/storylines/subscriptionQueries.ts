import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import {
  STORYLINES_QUERY_KEY,
  type StorylineCard,
  StorylineRequestError,
} from "@/features/storylines/storylineQueries";
import { useApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type StorylineSubscriptions =
  components["schemas"]["StorylineSubscriptionsResponse"];
export type SubscribeResult =
  components["schemas"]["StorylineSubscribeResponse"];
export type UnsubscribeResult =
  components["schemas"]["StorylineUnsubscribeResponse"];
export type SubscriptionChanges =
  components["schemas"]["StorylineSubscriptionChangesResponse"];
export type SubscriptionChange =
  components["schemas"]["StorylineSubscriptionChangeItem"];
export type SubscriptionAction = SubscriptionChange["action"];
export type StorylineCardsResult =
  components["schemas"]["StorylineCardsResponse"];

/** 스토리라인 ID → 카드 — 받지 못한(그새 지워진) ID 는 없다 */
export type CardsById = ReadonlyMap<number, StorylineCard>;

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

/** ID 여럿의 카드를 한 번에 — 받은 ID 로 찾게 맵으로 낸다(docs/specs/reader.md reader-R6) */
async function fetchCards(
  client: ReturnType<typeof useApiClient>,
  ids: number[],
): Promise<CardsById> {
  const { data, response } = await client.GET("/api/storyline-cards", {
    params: { query: { ids } },
  });
  if (!response.ok || !data) {
    throw new StorylineRequestError(response.status);
  }
  return new Map(
    data.cards.map((entry): [number, StorylineCard] => [
      entry.requestedId,
      entry.storyline,
    ]),
  );
}

/**
 * 내 구독의 카드 — 제목 · 상태 · 마지막 기사 시각. 카드 처리기는 빈 ids 를 받지 않으므로 구독이 없으면 묻지 않는다. 구독 수는 카드 한 번의
 * 상한 이하다(user-R63 — 서버 시험이 지킨다)
 */
export function useStorylineCards(ids: number[]) {
  const client = useApiClient();
  return useQuery({
    queryKey: [...STORYLINES_QUERY_KEY, "cards", ids],
    queryFn: () => fetchCards(client, ids),
    enabled: ids.length > 0,
  });
}

/** 구독 기록 한 쪽과 그 쪽 스토리라인의 카드 */
export type SubscriptionChangePage = {
  page: SubscriptionChanges;
  cards: CardsById;
};

type ChangeCursor = NonNullable<SubscriptionChanges["next"]>;

/**
 * 구독 기록 — 사용자가 직접 한 구독 · 해제를 최근부터 쪽마다(docs/specs/user.md user-R63). 다음 쪽은 받은 커서를 그대로 넘긴다. 쪽마다
 * 지금 스토리라인 ID 들의 카드를 함께 읽고, 그런 ID 가 없으면(모두 지워짐) 카드를 묻지 않는다. 구독 · 해제 뒤 다시 읽히게 구독 조회 키 아래에 둔다
 */
export function useSubscriptionChanges() {
  const client = useApiClient();
  return useInfiniteQuery({
    queryKey: [...STORYLINE_SUBSCRIPTIONS_QUERY_KEY, "changes"],
    initialPageParam: null as ChangeCursor | null,
    queryFn: async ({ pageParam }): Promise<SubscriptionChangePage> => {
      const { data, response } = await client.GET(
        "/api/storyline-subscriptions/changes",
        {
          params: {
            query: pageParam
              ? { beforeAt: pageParam.occurredAt, beforeId: pageParam.id }
              : {},
          },
        },
      );
      if (!response.ok || !data) {
        throw new StorylineRequestError(response.status);
      }
      const ids = [
        ...new Set(
          data.changes
            .map((change) => change.storylineId)
            .filter((id): id is number => id !== null),
        ),
      ];
      const cards = ids.length > 0 ? await fetchCards(client, ids) : new Map();
      return { page: data, cards };
    },
    getNextPageParam: (last): ChangeCursor | undefined =>
      last.page.next ?? undefined,
  });
}
