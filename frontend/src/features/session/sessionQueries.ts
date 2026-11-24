import { useQuery } from "@tanstack/react-query";
import { useApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type Me = components["schemas"]["MeResponse"];

/** 로그인 상태의 조회 키 접두 — 사용자가 바뀌어도 이 접두는 남기고 나머지 캐시를 비운다(userChange.ts) */
export const SESSION_QUERY_KEY = ["session"] as const;

/** 로그인 상태 조회의 키 */
export const ME_QUERY_KEY = [...SESSION_QUERY_KEY, "me"] as const;

/** 로그인 상태 — 로그인 여부와 무관하게 200 판정 결과다(docs/specs/account.md account-R20) */
export function useMe() {
  const client = useApiClient();
  return useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: async (): Promise<Me> => {
      const { data, response } = await client.GET("/api/me");
      if (!response.ok || !data) {
        throw new Error(
          `로그인 상태를 읽지 못했습니다 (HTTP ${response.status})`,
        );
      }
      return data;
    },
    retry: false,
  });
}
