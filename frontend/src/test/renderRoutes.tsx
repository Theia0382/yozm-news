import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { createMemoryRouter, type RouteObject } from "react-router";
import { RouterProvider } from "react-router/dom";
import { ApiClientContext, createApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";
import { fakeServer, type Handler, TEST_BASE_URL } from "@/test/fakeServer";

// 화면 시험의 그리기 — 경로 몇 개를 메모리 라우터에 싣고, 가짜 서버를 향한 클라이언트로 그린다.

export type Me = components["schemas"]["MeResponse"];

export const ANONYMOUS_ME: Me = {
  result: "ANONYMOUS",
  userId: null,
  email: null,
  displayName: null,
  role: null,
  passwordChangeRequired: false,
  llmKeyRequired: false,
};

export const SIGNED_IN_ME: Me = {
  result: "SIGNED_IN",
  userId: 7,
  email: "alice@example.com",
  displayName: "앨리스",
  role: "USER",
  passwordChangeRequired: false,
  llmKeyRequired: false,
};

export function renderRoutes(
  routes: RouteObject[],
  entry: string,
  handlers: Record<string, Handler>,
) {
  const server = fakeServer(handlers);
  const router = createMemoryRouter(routes, { initialEntries: [entry] });
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <ApiClientContext.Provider
        value={createApiClient({ baseUrl: TEST_BASE_URL, fetch: server.fetch })}
      >
        <RouterProvider router={router} />
      </ApiClientContext.Provider>
    </QueryClientProvider>,
  );
  return { server, router };
}

/** 받은 요청 가운데 메서드 · 경로가 같은 것의 본문들(JSON) */
export function bodiesOf(
  server: ReturnType<typeof fakeServer>,
  method: string,
  path: string,
): unknown[] {
  return server.requests
    .filter((r) => r.method === method && r.path === path)
    .map((r) => JSON.parse(r.body) as unknown);
}
