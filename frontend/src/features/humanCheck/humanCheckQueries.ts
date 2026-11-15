import { useQuery } from "@tanstack/react-query";
import { useApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type HumanCheckSetting = components["schemas"]["HumanCheckResponse"];

/** 봇 차단 설정의 조회 키 */
export const HUMAN_CHECK_QUERY_KEY = ["human-check"] as const;

/** 봇 차단 설정 — 로그인 없이 읽는다(docs/specs/user.md user-R41) */
export function useHumanCheck() {
  const client = useApiClient();
  return useQuery({
    queryKey: HUMAN_CHECK_QUERY_KEY,
    queryFn: async (): Promise<HumanCheckSetting> => {
      const { data, response } = await client.GET("/api/human-check");
      if (!response.ok || !data) {
        throw new Error(
          `봇 차단 설정을 읽지 못했습니다 (HTTP ${response.status})`,
        );
      }
      return data;
    },
    staleTime: Infinity,
    retry: false,
  });
}
