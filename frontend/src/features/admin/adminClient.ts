import type { ClientOptions } from "openapi-fetch";
import { createApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type Schemas = components["schemas"];
export type ApiProblem = Schemas["ApiProblem"];

const ADMIN_PATH = "/api/admin/";

/** 운영자 기능이 닫혔음을 뜻하는 문제 유형 — 서버가 403 과 함께 낸다 */
export const ADMIN_CLOSED = "urn:yozmnews:problem:admin-closed";

type AdminClientOptions = ClientOptions & {
  /** 보낼 운영자 토큰 — 없으면 헤더를 붙이지 않는다 */
  token: () => string | null;
  /** 운영자 API 가 401 로 답했을 때 */
  onUnauthorized?: () => void;
};

function isAdminPath(request: Request): boolean {
  return new URL(request.url).pathname.startsWith(ADMIN_PATH);
}

/** 운영자 API 클라이언트 — /api/admin/ 요청에만 Authorization: Bearer 를 붙인다 */
export function createAdminClient({
  token,
  onUnauthorized,
  ...options
}: AdminClientOptions) {
  const client = createApiClient(options);
  client.use({
    onRequest({ request }) {
      const value = token();
      if (value && isAdminPath(request)) {
        request.headers.set("Authorization", `Bearer ${value}`);
      }
      return request;
    },
    onResponse({ request, response }) {
      if (response.status === 401 && isAdminPath(request)) {
        onUnauthorized?.();
      }
      return response;
    },
  });
  return client;
}

export type AdminClient = ReturnType<typeof createAdminClient>;

/** 운영자 API 의 실패 — 상태 코드와 문제 응답 */
export class AdminApiError extends Error {
  readonly status: number;
  readonly problem: ApiProblem | undefined;

  constructor(status: number, problem: ApiProblem | undefined) {
    super(problem?.detail ?? `HTTP ${status}`);
    this.status = status;
    this.problem = problem;
  }
}

/** openapi-fetch 결과에서 값을 꺼낸다 — 실패면 AdminApiError 를 던진다 */
export async function unwrap<T>(
  result: Promise<{ data?: T; error?: unknown; response: Response }>,
): Promise<T> {
  const { data, error, response } = await result;
  if (!response.ok) {
    throw new AdminApiError(response.status, asProblem(error));
  }
  return data as T;
}

function asProblem(error: unknown): ApiProblem | undefined {
  if (error && typeof error === "object" && "status" in error) {
    return error as ApiProblem;
  }
  return undefined;
}

/** 실패를 운영자에게 보일 문구 — 문제 응답의 설명, 없으면 상태 코드 */
export function failureText(error: unknown): string {
  if (error instanceof AdminApiError) {
    return (
      error.problem?.detail ?? `요청이 실패했습니다 (HTTP ${error.status})`
    );
  }
  return "서버에 닿지 못했습니다";
}
