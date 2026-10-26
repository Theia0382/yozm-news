import createClient, { type ClientOptions } from "openapi-fetch";
import type { paths } from "./schema";

// 화면용 API 클라이언트. 경로 · 요청 · 응답 타입은 명세(../api/openapi.json)에서 생성한 schema.d.ts 를 따른다.
export function createApiClient(options?: ClientOptions) {
  return createClient<paths>(options);
}

// SPA 와 API 가 같은 오리진이라 baseUrl 없이 상대 경로로 요청한다.
export const apiClient = createApiClient();
