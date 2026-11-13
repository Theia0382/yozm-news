import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { useApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type NoticePage = components["schemas"]["NoticePageResponse"];
export type Notice = components["schemas"]["NoticeResponse"];
export type NoticeRevision = components["schemas"]["NoticeRevision"];

/** 알림 조회의 키 */
export const NOTICES_QUERY_KEY = ["notices"] as const;

const UNREAD_QUERY_KEY = [...NOTICES_QUERY_KEY, "unread"] as const;

/** 알림 요청이 실패했다 — 화면이 상태 코드로 가른다(401 이면 로그인 상태를 다시 읽는다) */
export class NoticeRequestError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`알림을 읽지 못했습니다 (HTTP ${status})`);
    this.status = status;
  }
}

/**
 * 안 읽은 알림 수 — 창 포커스 · 다시 연결 때 다시 읽고(기본값), 주기 조회는 하지 않는다. 화면 이동 때 다시 읽는 것은 머리줄의
 * 알림 링크가 한다(docs/specs/user.md user-R27).
 */
export function useUnreadNotices() {
  const client = useApiClient();
  return useQuery({
    queryKey: UNREAD_QUERY_KEY,
    queryFn: async (): Promise<number> => {
      const { data, response } = await client.GET("/api/notices/unread-count");
      if (!response.ok || !data) {
        throw new NoticeRequestError(response.status);
      }
      return data.count;
    },
  });
}

/**
 * 알림 목록 — 최근부터 쪽마다. 페이지에 머무는 동안 다시 읽지 않아 받은 응답(안 읽음 강조)이 그대로이고, 페이지를 떠나면 버려
 * 들어올 때마다 새로 받는다.
 */
export function useNoticePages() {
  const client = useApiClient();
  return useInfiniteQuery({
    queryKey: [...NOTICES_QUERY_KEY, "pages"],
    queryFn: async ({ pageParam }): Promise<NoticePage> => {
      const { data, response } = await client.GET("/api/notices", {
        params: { query: pageParam === null ? {} : { before: pageParam } },
      });
      if (!response.ok || !data) {
        throw new NoticeRequestError(response.status);
      }
      return data;
    },
    initialPageParam: null as number | null,
    getNextPageParam: (page) => page.nextBefore,
    staleTime: Infinity,
    gcTime: 0,
    retry: false,
  });
}

/** 받은 알림을 읽음으로 — 받은 판이 그대로인 알림만 바뀐다. 성공하면 안 읽은 수를 다시 읽는다 */
export function useMarkSeen() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (notices: NoticeRevision[]) => {
      const { response } = await client.POST("/api/notices/read", {
        body: { notices },
      });
      if (!response.ok) {
        throw new NoticeRequestError(response.status);
      }
    },
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: UNREAD_QUERY_KEY }),
  });
}
