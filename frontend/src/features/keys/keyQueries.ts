import { useQuery } from "@tanstack/react-query";
import { useApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type LlmKeys = components["schemas"]["LlmKeysResponse"];

/** 키 조회의 키 */
export const LLM_KEYS_QUERY_KEY = ["llm-keys"] as const;

/** 키 요청이 실패했다 — 화면이 상태 코드로 가른다(401 이면 로그인 상태를 다시 읽는다) */
export class KeyRequestError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`LLM 키를 읽지 못했습니다 (HTTP ${status})`);
    this.status = status;
  }
}

/** 세션 사용자의 키 — 공급자마다 하나(docs/specs/credential.md §3) */
export function useLlmKeys() {
  const client = useApiClient();
  return useQuery({
    queryKey: LLM_KEYS_QUERY_KEY,
    queryFn: async (): Promise<LlmKeys> => {
      const { data, response } = await client.GET("/api/llm-keys");
      if (!response.ok || !data) {
        throw new KeyRequestError(response.status);
      }
      return data;
    },
  });
}
