import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { retryUnlessKeyRequired } from "@/features/digests/digestQueries";
import { useApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type TopicSuggestions =
  components["schemas"]["TopicSuggestionsResponse"];
export type TopicSuggestion = components["schemas"]["TopicSuggestionResponse"];
export type SuggestionDeclineResult =
  components["schemas"]["TopicSuggestionDeclineResponse"];

/** 구독 제안 조회 키 */
export const TOPIC_SUGGESTIONS_QUERY_KEY = ["topic-suggestions"] as const;

/** 구독 제안 요청이 실패했다 — 화면이 상태 코드로 가른다 */
export class TopicSuggestionRequestError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`구독 제안을 처리하지 못했습니다 (HTTP ${status})`);
    this.status = status;
  }
}

/**
 * 세션 사용자에게 지금 제안할 주제(docs/specs/reader.md reader-R12). 403(LLM 키 없음)은 다시 묻지 않는다 — 다시 묻는 규칙은 홈 조회와 하나다
 */
export function useTopicSuggestions() {
  const client = useApiClient();
  return useQuery({
    queryKey: TOPIC_SUGGESTIONS_QUERY_KEY,
    queryFn: async (): Promise<TopicSuggestions> => {
      const { data, response } = await client.GET("/api/topic-suggestions");
      if (!response.ok || !data) {
        throw new TopicSuggestionRequestError(response.status);
      }
      return data;
    },
    retry: retryUnlessKeyRequired,
  });
}

/**
 * 구독 제안 거절(docs/specs/user.md user-R64) — 제안이 낸 태그 체계 버전을 싣는다. 적었든 버전이 바뀌었든 제안을 다시 읽는다
 */
export function useDeclineSuggestion() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      tagSetId,
      topic,
    }: {
      tagSetId: number;
      topic: string;
    }): Promise<SuggestionDeclineResult> => {
      const { data, response } = await client.POST(
        "/api/interests/declined-suggestions",
        { body: { tagSetId, topic } },
      );
      if (!response.ok || !data) {
        throw new TopicSuggestionRequestError(response.status);
      }
      return data;
    },
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: TOPIC_SUGGESTIONS_QUERY_KEY }),
  });
}
