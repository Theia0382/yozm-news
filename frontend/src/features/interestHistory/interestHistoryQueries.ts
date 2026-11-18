import { useQuery } from "@tanstack/react-query";
import { useApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type InterestHistory = components["schemas"]["InterestHistoryResponse"];
export type InterestPeriod = components["schemas"]["InterestPeriodResponse"];
export type InterestTopic = components["schemas"]["InterestTopicResponse"];
export type InterestSpanChoices =
  components["schemas"]["InterestSpanChoicesResponse"];
export type TopicSubscriptionStep =
  components["schemas"]["TopicSubscriptionStepResponse"];
export type InterestSpan = InterestHistory["span"];
export type InterestUnit = InterestSpanChoices["unit"];

/** 관심사 히스토리 조회 키의 접두 */
export const INTEREST_HISTORY_QUERY_KEY = ["interest-history"] as const;

/** 관심사 히스토리 요청이 실패했다 — 응답의 상태 코드를 싣는다 */
export class InterestHistoryRequestError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`관심사 히스토리를 읽지 못했습니다 (HTTP ${status})`);
    this.status = status;
  }
}

/**
 * 세션 사용자의 관심사 히스토리 — 기간을 고르지 않았으면(null) 기간 없이 부르고, 서버가 처음 보일 기간을 정해 응답의 span 으로 알린다
 * (docs/specs/reader.md reader-R8).
 */
export function useInterestHistory(span: InterestSpan | null) {
  const client = useApiClient();
  return useQuery({
    queryKey: [...INTEREST_HISTORY_QUERY_KEY, span],
    queryFn: async (): Promise<InterestHistory> => {
      const { data, response } = await client.GET("/api/interest-history", {
        params: { query: span === null ? {} : { span } },
      });
      if (!response.ok || !data) {
        throw new InterestHistoryRequestError(response.status);
      }
      return data;
    },
  });
}
