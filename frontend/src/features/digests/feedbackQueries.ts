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
export type FeedbackState = components["schemas"]["FeedbackItemState"]["value"];
export type FeedbackResult = components["schemas"]["FeedbackResponse"];

/** 피드백 조회 키의 접두 — 항목 상태 조회와 기록의 변경 키는 모두 이 아래 */
export const FEEDBACK_QUERY_KEY = ["feedback"] as const;

/** 다이제스트 하나의 항목 상태 조회 키 */
function itemsKey(digestId: number): QueryKey {
  return [...FEEDBACK_QUERY_KEY, "items", digestId];
}

/** 항목 키 → 그 항목에 누른 값(좋아요 · 싫어요). 누른 값이 없는 항목은 없다 */
export type FeedbackStates = ReadonlyMap<number, FeedbackState>;

/** 반응을 받는 항목 — 항목 키(그 항목의 첫 사건 ID)와 버튼 묶음 이름에 쓸 제목 */
export type FeedbackItem = { key: number; title: string };

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
 * 섹션이 반응을 받는 항목이면 그 항목 — 주요 뉴스 · 헤드라인 항목이고 항목 키는 섹션의 첫 사건(eventIds 의 첫 원소)이다. 개요는 항목이
 * 아니다(docs/specs/digest.md digest-R7)
 */
export function feedbackItemOf(section: DigestSection): FeedbackItem | null {
  if (section.kind !== "TOP_STORY" && section.kind !== "HEADLINE") {
    return null;
  }
  const key = section.eventIds[0];
  return key === undefined || section.title === null
    ? null
    : { key, title: section.title };
}

/**
 * 다이제스트 하나에서 세션 사용자가 항목마다 누른 값(docs/specs/feedback.md feedback-R7). 반응을 받는 항목이 없으면(enabled 가 거짓)
 * 묻지 않는다
 */
export function useFeedbackStates(digestId: number, enabled: boolean) {
  const client = useApiClient();
  return useQuery({
    queryKey: itemsKey(digestId),
    queryFn: async (): Promise<FeedbackStates> => {
      const { data, error, response } = await client.GET("/api/feedback", {
        params: { query: { digestId } },
      });
      if (!response.ok || !data) {
        throw new FeedbackRequestError(response, error);
      }
      return new Map(
        data.items.map((item): [number, FeedbackState] => [
          item.eventId,
          item.value,
        ]),
      );
    },
    enabled,
  });
}

/** 누른 버튼과 지금 상태로 보낼 값 — 눌린 버튼을 다시 누르면 되돌리고(없음), 아니면 누른 값이다 */
export function nextFeedback(
  current: FeedbackState | null,
  pressed: FeedbackState,
): FeedbackValue {
  return current === pressed ? "NONE" : pressed;
}

function recordKey(digestId: number, itemKey: number): QueryKey {
  return [...FEEDBACK_QUERY_KEY, "record", digestId, itemKey];
}

/**
 * 그 다이제스트의 항목 상태 조회에 그 항목의 지금 값을 써 넣는다 — 다른 다이제스트(다른 날짜)의 항목은 같은 사건을 담아도 그 항목에 누른
 * 값만 보이므로 건드리지 않는다. 써 넣기 전에 진행 중인 다시 읽기를 멈춘다 — 누르기 전에 시작된 읽기가 뒤늦게 옛 값으로 덮지 않게 한다. 값이
 * 없는 조회(처음 읽기를 멈춘 조회는 값 없이 읽기 전으로 돌아가 스스로 다시 읽지 않는다)는 써 넣지 않고 지금 다시 읽는다 — 이 응답 뒤의
 * 읽기라 지금 값을 받는다
 */
async function writeState(
  queryClient: QueryClient,
  digestId: number,
  itemKey: number,
  value: FeedbackValue,
): Promise<void> {
  const key = itemsKey(digestId);
  await queryClient.cancelQueries({ queryKey: key, exact: true });
  const states = queryClient.getQueryData<FeedbackStates>(key);
  if (states === undefined) {
    await queryClient.refetchQueries({ queryKey: key, exact: true });
    return;
  }
  const next = new Map(states);
  if (value === "NONE") {
    next.delete(itemKey);
  } else {
    next.set(itemKey, value);
  }
  queryClient.setQueryData<FeedbackStates>(key, next);
}

/** 결과마다 응답 뒤에 할 일 — 결과가 늘면 타입 검사가 잡는다 */
async function settle(
  queryClient: QueryClient,
  digestId: number,
  itemKey: number,
  result: FeedbackResult,
): Promise<void> {
  switch (result.result) {
    case "RECORDED":
    case "UNCHANGED":
      // 응답 값은 요청 뒤의 지금 값이다 — 이 두 결과에서 null 이 아니다(FeedbackResponse)
      if (result.value !== null) {
        await writeState(queryClient, digestId, itemKey, result.value);
      }
      return;
    case "NOT_IN_DIGEST":
      // 화면을 열어 둔 사이 항목의 인용 기사가 모두 지워져 항목이 없어졌다 — 다시 읽은 다이제스트는 그 항목을 뺀다(docs/specs/reader.md reader-R9)
      await queryClient.invalidateQueries({ queryKey: DIGESTS_QUERY_KEY });
      return;
  }
  const unknown: never = result.result;
  throw new Error(`알 수 없는 결과입니다: ${String(unknown)}`);
}

/**
 * 항목 하나의 누르기 · 되돌리기 — 서버 응답 뒤에 반영한다(낙관적 반영 없음). 요청 경로는 항목 키이고, 누른 다이제스트는 그 항목을 그린
 * 보이는 다이제스트다(docs/specs/feedback.md feedback-R2)
 */
export function useRecordFeedback(digestId: number, itemKey: number) {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: recordKey(digestId, itemKey),
    mutationFn: async (value: FeedbackValue): Promise<FeedbackResult> => {
      const { data, error, response } = await client.PUT(
        "/api/feedback/{eventId}",
        {
          params: { path: { eventId: itemKey } },
          body: { digestId, value },
        },
      );
      if (!response.ok || !data) {
        throw new FeedbackRequestError(response, error);
      }
      return data;
    },
    onSuccess: (result) => settle(queryClient, digestId, itemKey, result),
  });
}

/** 그 항목의 기록이 진행 중인가 */
export function useFeedbackPending(digestId: number, itemKey: number): boolean {
  return useIsMutating({ mutationKey: recordKey(digestId, itemKey) }) > 0;
}
