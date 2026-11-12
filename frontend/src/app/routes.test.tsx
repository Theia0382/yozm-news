import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { createMemoryRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { describe, expect, it } from "vitest";
import { routes } from "@/app/routes";
import { BigStorylinesPage } from "@/features/admin/BigStorylinesPage";
import { CollectionPage } from "@/features/admin/CollectionPage";
import { EvaluationDetailPage } from "@/features/admin/EvaluationDetailPage";
import { EvaluationListPage } from "@/features/admin/EvaluationListPage";
import { EvaluationPage } from "@/features/admin/EvaluationPage";
import { EventDetailPage } from "@/features/admin/EventDetailPage";
import { EventJudgementsPage } from "@/features/admin/EventJudgementsPage";
import { EventPublicationsPage } from "@/features/admin/EventPublicationsPage";
import { EventsPage } from "@/features/admin/EventsPage";
import { SamplePage } from "@/features/admin/SamplePage";
import { ThresholdsPage } from "@/features/admin/ThresholdsPage";
import { ApiClientContext, createApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";
import { fakeServer, json, TEST_BASE_URL } from "@/test/fakeServer";

/** 로그인 상태를 정한 가짜 서버로 경로를 그린다 */
function renderAt(path: string, me: components["schemas"]["MeResponse"]) {
  const server = fakeServer({ "GET /api/me": () => json(me) });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ApiClientContext.Provider
        value={createApiClient({ baseUrl: TEST_BASE_URL, fetch: server.fetch })}
      >
        <RouterProvider router={router} />
      </ApiClientContext.Provider>
    </QueryClientProvider>,
  );
  return router;
}

describe("routes", () => {
  it("로그인한 사용자에게 루트 경로의 첫 화면을 보여준다", async () => {
    renderAt("/", {
      result: "SIGNED_IN",
      userId: 1,
      email: "a@example.com",
      displayName: "앨리스",
      role: "USER",
    });

    expect(
      await screen.findByRole("heading", { level: 1, name: "요즘뉴스" }),
    ).toBeInTheDocument();
  });

  it("로그인하지 않은 방문자는 루트 경로에서 로그인 화면으로 간다", async () => {
    const router = renderAt("/", {
      result: "ANONYMOUS",
      userId: null,
      email: null,
      displayName: null,
      role: null,
    });

    expect(
      await screen.findByRole("heading", { level: 1, name: "로그인" }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
    expect(router.state.location.search).toBe("?next=%2F");
  });

  it("운영자 화면 아래 수집 현황 경로가 수집 현황 화면을 내려받는다", async () => {
    const admin = routes[0]?.children?.find((route) => route.path === "admin");
    const collection = admin?.children?.find(
      (route) => route.path === "collection",
    );
    const lazy = collection?.lazy;

    expect(typeof lazy).toBe("function");
    const loaded = await (lazy as () => Promise<{ Component?: unknown }>)();
    expect(loaded.Component).toBe(CollectionPage);
  });

  it("운영자 화면 아래 평가 현황 경로가 평가 현황 화면을 내려받는다", async () => {
    const admin = routes[0]?.children?.find((route) => route.path === "admin");
    const evaluation = admin?.children?.find(
      (route) => route.path === "evaluation",
    );
    const lazy = evaluation?.lazy;

    expect(typeof lazy).toBe("function");
    const loaded = await (lazy as () => Promise<{ Component?: unknown }>)();
    expect(loaded.Component).toBe(EvaluationPage);
  });

  it.each([
    ["evaluations", "EvaluationListPage", () => EvaluationListPage],
    ["evaluations/:id", "EvaluationDetailPage", () => EvaluationDetailPage],
    ["samples", "SamplePage", () => SamplePage],
    ["thresholds", "ThresholdsPage", () => ThresholdsPage],
    ["events", "EventsPage", () => EventsPage],
    ["events/:id", "EventDetailPage", () => EventDetailPage],
    ["big-storylines", "BigStorylinesPage", () => BigStorylinesPage],
    ["event-judgements", "EventJudgementsPage", () => EventJudgementsPage],
  ])(
    "운영자 화면 아래 %s 경로가 %s 를 내려받는다",
    async (path, _name, component) => {
      const admin = routes[0]?.children?.find(
        (route) => route.path === "admin",
      );
      const lazy = admin?.children?.find((route) => route.path === path)?.lazy;

      expect(typeof lazy).toBe("function");
      const loaded = await (lazy as () => Promise<{ Component?: unknown }>)();
      expect(loaded.Component).toBe(component());
    },
  );

  it("운영자 화면 아래 이벤트 발행 경로가 이벤트 발행 화면을 내려받는다", async () => {
    const admin = routes[0]?.children?.find((route) => route.path === "admin");
    const publications = admin?.children?.find(
      (route) => route.path === "event-publications",
    );
    const lazy = publications?.lazy;

    expect(typeof lazy).toBe("function");
    const loaded = await (lazy as () => Promise<{ Component?: unknown }>)();
    expect(loaded.Component).toBe(EventPublicationsPage);
  });
});
