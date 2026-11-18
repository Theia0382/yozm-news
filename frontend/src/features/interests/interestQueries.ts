import { useQuery } from "@tanstack/react-query";
import { useApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type InterestTags = components["schemas"]["InterestTagsResponse"];

export type InterestSources = components["schemas"]["InterestSourcesResponse"];

export type InterestSettings =
  components["schemas"]["InterestSettingsResponse"];

export type InterestModel = components["schemas"]["InterestModelResponse"];

export type InterestVolume = components["schemas"]["InterestVolumeResponse"];

/** 관심 설정 조회의 키 — 갈래마다 하나 */
export const INTEREST_TAGS_QUERY_KEY = ["interests", "tags"] as const;
export const INTEREST_SOURCES_QUERY_KEY = ["interests", "sources"] as const;
export const INTEREST_SETTINGS_QUERY_KEY = ["interests", "settings"] as const;
export const INTEREST_MODEL_QUERY_KEY = ["interests", "model"] as const;
export const INTEREST_VOLUME_QUERY_KEY = ["interests", "volume"] as const;

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

/** 일정 · 관심사 · 리포트 */
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
