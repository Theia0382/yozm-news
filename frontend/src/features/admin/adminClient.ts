import type { ClientOptions } from "openapi-fetch";
import { createApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type Schemas = components["schemas"];
export type ApiProblem = Schemas["ApiProblem"];

const ADMIN_PATH = "/api/admin/";

type AdminClientOptions = ClientOptions & {
  /** 운영자 API 가 401 로 답했을 때 — 세션이 없거나 끝났다 */
  onUnauthorized?: () => void;
};

function isAdminPath(request: Request): boolean {
  return new URL(request.url).pathname.startsWith(ADMIN_PATH);
}

/**
 * 운영자 API 클라이언트 — 운영자 계정 세션(쿠키)으로 부르고, 변경 요청에는 화면용 API 클라이언트처럼 요청 보호 토큰을 붙인다.
 * /api/admin/ 의 401 만 알린다.
 */
export function createAdminClient({
  onUnauthorized,
  ...options
}: AdminClientOptions) {
  const client = createApiClient(options);
  client.use({
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
