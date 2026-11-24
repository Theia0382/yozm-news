import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { TOPIC_SUGGESTIONS_QUERY_KEY } from "@/features/digests/topicSuggestionQueries";
import { useApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type InterestTags = components["schemas"]["InterestTagsResponse"];

export type InterestSources = components["schemas"]["InterestSourcesResponse"];

export type InterestSettings =
  components["schemas"]["InterestSettingsResponse"];

export type InterestModel = components["schemas"]["InterestModelResponse"];

export type InterestVolume = components["schemas"]["InterestVolumeResponse"];

/** 관심 설정 조회의 공통 접두 — 화면이 조회가 모두 끝났는지 이것으로 본다 */
export const INTERESTS_QUERY_KEY = ["interests"] as const;

/** 관심 설정 조회의 키 — 갈래마다 하나 */
export const INTEREST_TAGS_QUERY_KEY = [
  ...INTERESTS_QUERY_KEY,
  "tags",
] as const;
export const INTEREST_SOURCES_QUERY_KEY = [
  ...INTERESTS_QUERY_KEY,
  "sources",
] as const;
export const INTEREST_SETTINGS_QUERY_KEY = [
  ...INTERESTS_QUERY_KEY,
  "settings",
] as const;
export const INTEREST_MODEL_QUERY_KEY = [
  ...INTERESTS_QUERY_KEY,
  "model",
] as const;
export const INTEREST_VOLUME_QUERY_KEY = [
  ...INTERESTS_QUERY_KEY,
  "volume",
] as const;

/** 관심 설정 요청이 실패했다 — 화면이 상태 코드로 가른다 */
export class InterestRequestError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`관심 설정을 읽지 못했습니다 (HTTP ${status})`);
    this.status = status;
  }
}

/** 활성 태그 체계와 세션 사용자의 태그 선호(docs/specs/user.md §3) */
export function useInterestTags() {
  const client = useApiClient();
  return useQuery({
    queryKey: INTEREST_TAGS_QUERY_KEY,
    queryFn: async (): Promise<InterestTags> => {
      const { data, response } = await client.GET("/api/interests/tags");
      if (!response.ok || !data) {
        throw new InterestRequestError(response.status);
      }
      return data;
    },
  });
}

/** 고를 소스와 제외 소스 */
export function useInterestSources() {
  const client = useApiClient();
  return useQuery({
    queryKey: INTEREST_SOURCES_QUERY_KEY,
    queryFn: async (): Promise<InterestSources> => {
      const { data, response } = await client.GET("/api/interests/sources");
      if (!response.ok || !data) {
        throw new InterestRequestError(response.status);
      }
      return data;
    },
  });
}

/** 일정 · 리포트 */
export function useInterestSettings() {
  const client = useApiClient();
  return useQuery({
    queryKey: INTEREST_SETTINGS_QUERY_KEY,
    queryFn: async (): Promise<InterestSettings> => {
      const { data, response } = await client.GET("/api/interests/settings");
      if (!response.ok || !data) {
        throw new InterestRequestError(response.status);
      }
      return data;
    },
  });
}

/** 고를 수 있는 모델 · 고른 모델 · 쓸 모델 */
export function useInterestModel() {
  const client = useApiClient();
  return useQuery({
    queryKey: INTEREST_MODEL_QUERY_KEY,
    queryFn: async (): Promise<InterestModel> => {
      const { data, response } = await client.GET("/api/interests/model");
      if (!response.ok || !data) {
        throw new InterestRequestError(response.status);
      }
      return data;
    },
  });
}

/** 다이제스트 분량 — 최대 후보 수의 단계 · 직접 입력 · 큰 슬롯 수와 단계의 값 · 범위 */
export function useInterestVolume() {
  const client = useApiClient();
  return useQuery({
    queryKey: INTEREST_VOLUME_QUERY_KEY,
    queryFn: async (): Promise<InterestVolume> => {
      const { data, response } = await client.GET("/api/interests/volume");
      if (!response.ok || !data) {
        throw new InterestRequestError(response.status);
      }
      return data;
    },
  });
}

/** 제안한 주제를 구독한 결과 — 서버의 저장 결과, 또는 제안과 선호의 체계 버전이 달라 보내지 않음 */
export type SubscribeTopicResult =
  components["schemas"]["InterestTagsSaveResponse"]["result"] | "STALE";

/**
 * 주제 하나를 구독에 더한다 — 지금 선호(주제 · 지역 · 형식)에 키를 더해 태그 선호 저장(docs/specs/user.md user-R46)으로 보낸다. 제안이 낸
 * 체계 버전과 선호의 버전이 다르면 보내지 않는다. 어느 쪽이든 끝나면 관심 설정과 구독 제안을 다시 읽는다
 */
export function useSubscribeTopic() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      tags,
      tagSetId,
      topic,
    }: {
      tags: InterestTags;
      tagSetId: number;
      topic: string;
    }): Promise<SubscribeTopicResult> => {
      if (tags.tagSet.id !== tagSetId) {
        return "STALE";
      }
      const { data, response } = await client.PUT("/api/interests/tags", {
        body: {
          tagSetId,
          topics: [...tags.preferences.topics, topic],
          excludedRegions: tags.preferences.excludedRegions,
          excludedFormats: tags.preferences.excludedFormats,
        },
      });
      if (!response.ok || !data) {
        throw new InterestRequestError(response.status);
      }
      return data.result;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: INTERESTS_QUERY_KEY });
      await queryClient.invalidateQueries({
        queryKey: TOPIC_SUGGESTIONS_QUERY_KEY,
      });
    },
  });
}
