import {
  useIsMutating,
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
  type QueryKey,
} from "@tanstack/react-query";
import {
  DIGESTS_QUERY_KEY,
  type DigestSection,
} from "@/features/digests/digestQueries";
import { useApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type FeedbackValue = components["schemas"]["FeedbackRequest"]["value"];
export type FeedbackState = components["schemas"]["FeedbackStateItem"]["value"];
export type FeedbackResult = components["schemas"]["FeedbackResponse"];

/** 피드백 조회 키의 접두 — 현재 상태 조회와 기록의 변경 키는 모두 이 아래 */
export const FEEDBACK_QUERY_KEY = ["feedback"] as const;

/** 현재 상태 조회 키의 접두 — 셋째 원소가 물은 기사 ID 목록이다 */
const STATES_KEY = [...FEEDBACK_QUERY_KEY, "states"] as const;

/** 기사 ID → 현재 상태(좋아요 · 싫어요). 현재 상태가 없는 기사는 없다 */
export type FeedbackStates = ReadonlyMap<number, FeedbackState>;

/** 피드백 요청이 실패했다(2xx 가 아님) — 화면이 응답 · 문제 본문으로 안내를 고른다 */
export class FeedbackRequestError extends Error {
  readonly response: Response;
  readonly problem: unknown;

  constructor(response: Response, problem: unknown) {
    super(`좋아요 · 싫어요 요청이 실패했습니다 (HTTP ${response.status})`);
    this.response = response;
    this.problem = problem;
  }
}

/**
 * 상태를 물을 기사 ID — 주요 뉴스 · 추가 헤드라인의 인용 기사(개요는 인용이 없다 — docs/specs/digest.md digest-R7). 중복을 없애고
 * 오름차순으로 정규화해, 같은 다이제스트는 인용 순서 · 겹침과 무관하게 같은 조회 키가 된다
 */
export function feedbackArticleIds(sections: DigestSection[]): number[] {
  const ids = new Set<number>();
  for (const section of sections) {
    if (section.kind === "TOP_STORY" || section.kind === "MORE_HEADLINES") {
      for (const citation of section.citations) {
        ids.add(citation.articleId);
      }
    }
  }
  return [...ids].sort((a, b) => a - b);
}

/**
 * 현재 상태 — 세션 사용자의 것(docs/specs/feedback.md feedback-R7). ids 가 비었으면 묻지 않는다 — 빈 목록은 400 이다(feedback-R9)
 */
export function useFeedbackStates(ids: number[]) {
  const client = useApiClient();
  return useQuery({
    queryKey: [...STATES_KEY, ids],
    queryFn: async (): Promise<FeedbackStates> => {
      const { data, error, response } = await client.GET("/api/feedback", {
        params: { query: { articleIds: ids } },
      });
      if (!response.ok || !data) {
        throw new FeedbackRequestError(response, error);
      }
      return new Map(
        data.states.map((state): [number, FeedbackState] => [
          state.articleId,
          state.value,
        ]),
      );
    },
    enabled: ids.length > 0,
  });
}

/** 누른 버튼과 지금 상태로 보낼 값 — 눌린 버튼을 다시 누르면 되돌리고(없음), 아니면 누른 값이다 */
export function nextFeedback(
  current: FeedbackState | null,
  pressed: FeedbackState,
): FeedbackValue {
  return current === pressed ? "NONE" : pressed;
}

/** 누르기 · 되돌리기 한 번의 입력 — 기사 ID 는 훅이 갖는다(변경 키와 요청 경로가 같은 값을 쓴다) */
export type FeedbackPress = { digestId: number; value: FeedbackValue };

function recordKey(articleId: number): QueryKey {
  return [...FEEDBACK_QUERY_KEY, "record", articleId];
}

/** 현재 상태 조회 가운데 물은 기사 ID 목록에 그 기사가 있는 것 */
function statesHolding(articleId: number) {
  return {
    queryKey: STATES_KEY,
    predicate: (query: { queryKey: QueryKey }) => {
      const ids = query.queryKey[STATES_KEY.length];
      return Array.isArray(ids) && ids.includes(articleId);
    },
  };
}

/**
 * 그 기사를 담은 현재 상태 조회에 지금 값을 써 넣는다 — 같은 기사의 모든 자리 · 캐시된 다른 날짜가 함께 바뀐다. 써 넣기 전에 진행 중인 다시
 * 읽기를 멈춘다 — 누르기 전에 시작된 읽기가 뒤늦게 옛 값으로 덮지 않게 한다. 처음 읽기를 멈춘 조회는 값 없이 읽기 전으로 돌아가 스스로 다시
 * 읽지 않으므로(그동안 버튼을 누를 수 없다) 써 넣지 않고 지금 다시 읽는다 — 이 응답 뒤의 읽기라 지금 값을 받는다
 */
async function writeState(
  queryClient: QueryClient,
  articleId: number,
  value: FeedbackValue,
): Promise<void> {
  const filters = statesHolding(articleId);
  await queryClient.cancelQueries(filters);
  for (const [key, states] of queryClient.getQueriesData<FeedbackStates>(
    filters,
  )) {
    if (states === undefined) {
      continue;
    }
    const next = new Map(states);
    if (value === "NONE") {
      next.delete(articleId);
    } else {
      next.set(articleId, value);
    }
    queryClient.setQueryData<FeedbackStates>(key, next);
  }
  await queryClient.refetchQueries({
    queryKey: STATES_KEY,
    predicate: (query) =>
      filters.predicate(query) && query.state.data === undefined,
  });
}

/** 결과마다 응답 뒤에 할 일 — 결과가 늘면 타입 검사가 잡는다 */
async function settle(
  queryClient: QueryClient,
  articleId: number,
  result: FeedbackResult,
): Promise<void> {
  switch (result.result) {
    case "RECORDED":
    case "UNCHANGED":
      // 응답 값은 요청 뒤의 지금 값이다 — 이 두 결과에서 null 이 아니다(FeedbackResponse)
      if (result.value !== null) {
        await writeState(queryClient, articleId, result.value);
      }
      return;
    case "NOT_IN_DIGEST":
      // 화면을 열어 둔 사이 기사가 지워졌다 — 다시 읽은 다이제스트는 그 인용을 뺀다(docs/specs/reader.md reader-R9)
      await queryClient.invalidateQueries({ queryKey: DIGESTS_QUERY_KEY });
      return;
  }
  const unknown: never = result.result;
  throw new Error(`알 수 없는 결과입니다: ${String(unknown)}`);
}

/**
 * 기사 하나의 누르기 · 되돌리기 — 서버 응답 뒤에 반영한다(낙관적 반영 없음). 누른 다이제스트는 그 인용을 그린 보이는 다이제스트다
 * (docs/specs/feedback.md feedback-R2)
 */
export function useRecordFeedback(articleId: number) {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: recordKey(articleId),
    mutationFn: async ({
      digestId,
      value,
    }: FeedbackPress): Promise<FeedbackResult> => {
      const { data, error, response } = await client.PUT(
        "/api/feedback/{articleId}",
        { params: { path: { articleId } }, body: { digestId, value } },
      );
      if (!response.ok || !data) {
        throw new FeedbackRequestError(response, error);
      }
      return data;
    },
    onSuccess: (result) => settle(queryClient, articleId, result),
  });
}

/** 그 기사의 기록이 진행 중인가 — 같은 기사의 모든 자리가 함께 잠긴다 */
export function useFeedbackPending(articleId: number): boolean {
  return useIsMutating({ mutationKey: recordKey(articleId) }) > 0;
}
