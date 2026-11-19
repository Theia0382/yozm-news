import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { useApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type DigestHome = components["schemas"]["DigestHomeResponse"];
export type DigestDay = components["schemas"]["DigestDayResponse"];
export type ShownDigest = components["schemas"]["ShownDigestResponse"];
export type DigestSection = components["schemas"]["DigestSectionResponse"];
export type DigestCitation = components["schemas"]["DigestCitationResponse"];
export type DigestAttempt = components["schemas"]["DigestAttemptResponse"];
export type DigestUsage = components["schemas"]["DigestUsageResponse"];
export type DigestDates = components["schemas"]["DigestDatesResponse"];
export type DigestRequestStatus =
  components["schemas"]["DigestRequestStatusResponse"];
export type DigestRequestResult =
  components["schemas"]["DigestRequestResponse"];
export type DigestStatus = DigestAttempt["status"];
export type DigestFailure = NonNullable<DigestAttempt["failure"]>;
export type DigestFailureCause = NonNullable<DigestAttempt["cause"]>;
export type SelectionReason = NonNullable<DigestSection["reason"]>;
export type DigestDayState = DigestDates["dates"][number]["state"];

/** 독자 화면의 다이제스트 조회 키의 접두 */
export const DIGESTS_QUERY_KEY = ["digests"] as const;

/** 만드는 중일 때 다시 읽는 간격(밀리초) — 측정 전 목표 */
export const POLL_INTERVAL_MS = 5000;

/** 다이제스트 요청이 실패했다 — 화면이 상태 코드로 가른다 */
export class DigestRequestError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`다이제스트를 읽지 못했습니다 (HTTP ${status})`);
    this.status = status;
  }
}

/**
 * 조회를 다시 물을지 — 키 관문의 403(LLM 키 없음)은 다시 묻지 않고, 그 밖의 실패는 세 번까지 다시 묻는다. 홈과 구독 제안의 조회가 함께 쓴다
 */
export function retryUnlessKeyRequired(
  failureCount: number,
  error: Error,
): boolean {
  const keyRequired = "status" in error && error.status === 403;
  return !keyRequired && failureCount < 3;
}

/** 생성을 기다리거나 생성 중인 상태인가 — 상태가 늘면 타입 검사가 잡는다 */
const runningStatuses = {
  SCHEDULED: true,
  RUNNING: true,
  READY: false,
  EMPTY: false,
  FAILED: false,
} as const satisfies Record<DigestStatus, boolean>;

export function isRunning(status: DigestStatus): boolean {
  return runningStatuses[status];
}

/**
 * 홈 — 가장 늦은 날짜와 수동 생성 상태. 만드는 중이면 끝날 때까지 다시 읽는다. 403(LLM 키 없음)은 다시 묻지 않는다 — 알림 화면도 이 조회로
 * 해결되지 않은 마지막 실패를 읽는데, 키가 없는 사용자에게 기본 재시도를 돌리지 않는다
 */
export function useDigestHome() {
  const client = useApiClient();
  return useQuery({
    queryKey: [...DIGESTS_QUERY_KEY, "home"],
    queryFn: async (): Promise<DigestHome> => {
      const { data, response } = await client.GET("/api/digests/latest");
      if (!response.ok || !data) {
        throw new DigestRequestError(response.status);
      }
      return data;
    },
    retry: retryUnlessKeyRequired,
    refetchInterval: (query) => {
      const home = query.state.data;
      const running =
        home !== undefined &&
        ((home.status?.inProgress ?? false) ||
          (home.day !== null && isRunning(home.day.latest.status)));
      return running ? POLL_INTERVAL_MS : false;
    },
  });
}

/** 날짜 하나 — 가장 최근 시도가 만드는 중이면 끝날 때까지 다시 읽는다 */
export function useDigestDay(date: string) {
  const client = useApiClient();
  return useQuery({
    queryKey: [...DIGESTS_QUERY_KEY, "day", date],
    queryFn: async (): Promise<DigestDay> => {
      const { data, response } = await client.GET("/api/digests/{date}", {
        params: { path: { date } },
      });
      if (!response.ok || !data) {
        throw new DigestRequestError(response.status);
      }
      return data;
    },
    refetchInterval: (query) => {
      const day = query.state.data;
      return day !== undefined && isRunning(day.latest.status)
        ? POLL_INTERVAL_MS
        : false;
    },
  });
}

/** 지난 다이제스트 날짜 — 다음 쪽은 앞 응답의 next 를 before 로 넘긴다 */
export function useDigestDates() {
  const client = useApiClient();
  return useInfiniteQuery({
    queryKey: [...DIGESTS_QUERY_KEY, "dates"],
    initialPageParam: null as string | null,
    queryFn: async ({ pageParam }): Promise<DigestDates> => {
      const { data, response } = await client.GET("/api/digests", {
        params: { query: pageParam === null ? {} : { before: pageParam } },
      });
      if (!response.ok || !data) {
        throw new DigestRequestError(response.status);
      }
      return data;
    },
    getNextPageParam: (page): string | undefined => page.next ?? undefined,
  });
}

/** 수동 생성 요청 — 끝나면 다이제스트 조회를 다시 읽는다 */
export function useRequestDigest() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (): Promise<DigestRequestResult> => {
      const { data, response } = await client.POST("/api/digests");
      if (!response.ok || !data) {
        throw new DigestRequestError(response.status);
      }
      return data;
    },
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: DIGESTS_QUERY_KEY }),
  });
}
