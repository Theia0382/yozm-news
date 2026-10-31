// 운영자 화면 시험의 가짜 서버 — "메서드 경로" 마다 응답을 정하고, 받은 요청을 남긴다.

export const TEST_BASE_URL = "http://localhost";

export type Handler = (request: Request) => Response | Promise<Response>;

export type RecordedRequest = {
  method: string;
  path: string;
  authorization: string | null;
  body: string;
};

export function fakeServer(handlers: Record<string, Handler>) {
  const requests: RecordedRequest[] = [];
  const fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const request = input instanceof Request ? input : new Request(input, init);
    const path = new URL(request.url).pathname;
    requests.push({
      method: request.method,
      path,
      authorization: request.headers.get("Authorization"),
      body: request.method === "GET" ? "" : await request.clone().text(),
    });
    const handler = handlers[`${request.method} ${path}`];
    if (!handler) {
      return problem(404, `시험이 정하지 않은 요청: ${request.method} ${path}`);
    }
    return handler(request);
  };
  return { fetch: fetch as typeof globalThis.fetch, requests, handlers };
}

/** 문제 응답(application/problem+json) */
export function problem(
  status: number,
  detail: string,
  type = "about:blank",
): Response {
  return new Response(
    JSON.stringify({ type, title: "", status, detail, instance: "" }),
    { status, headers: { "Content-Type": "application/problem+json" } },
  );
}

export function json(body: unknown, status = 200): Response {
  return Response.json(body, { status });
}

export function noContent(): Response {
  return new Response(null, { status: 204 });
}
