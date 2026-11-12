import createClient, { type ClientOptions } from "openapi-fetch";
import { createContext, useContext } from "react";
import { csrfMiddleware } from "./csrf";
import type { paths } from "./schema";

// 화면용 API 클라이언트. 경로 · 요청 · 응답 타입은 명세(저장소 루트 api/openapi.json)에서 생성한 schema.d.ts 를 따른다.
// 변경 요청에는 언제나 CSRF 헤더를 붙인다.
export function createApiClient(options?: ClientOptions) {
  const client = createClient<paths>(options);
  client.use(csrfMiddleware());
  return client;
}

export type ApiClient = ReturnType<typeof createApiClient>;

// SPA 와 API 가 같은 오리진이라 baseUrl 없이 상대 경로로 요청한다.
export const apiClient = createApiClient();

/** 화면이 쓰는 API 클라이언트 — 시험은 가짜 서버를 향한 클라이언트를 넣는다 */
export const ApiClientContext = createContext<ApiClient>(apiClient);

export function useApiClient(): ApiClient {
  return useContext(ApiClientContext);
}
