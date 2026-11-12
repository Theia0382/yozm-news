// 평가 검수 · 태그 기준 화면 시험의 렌더 도우미와 픽스처 — 가짜 서버 · 클라이언트 · 메모리 라우터를 한 번에 세운다.
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import type { ReactNode } from "react";
import { createMemoryRouter, type RouteObject } from "react-router";
import { RouterProvider } from "react-router/dom";
import { createAdminClient, type Schemas } from "@/features/admin/adminClient";
import { AdminSessionContext } from "@/features/admin/adminSession";
import { fakeServer, type Handler, TEST_BASE_URL } from "@/test/fakeServer";

export const TAG_SETS: Schemas["TagSetListResponse"] = {
  versions: [
    {
      id: 7,
      number: 2,
      active: true,
      createdBy: "OPERATOR",
      createdAt: "2026-11-02T00:00:00Z",
    },
    {
      id: 3,
      number: 1,
      active: false,
      createdBy: "SYSTEM",
      createdAt: "2026-10-30T00:00:00Z",
    },
  ],
  maxReevaluationDays: 30,
};

const share = (count: number) => ({ count, ratio: count / 4 });

export const OVERVIEW: Schemas["EvaluationOverviewResponse"] = {
  version: 7,
  from: "2026-10-27T15:00:00Z",
  to: "2026-11-03T15:00:00Z",
  evaluations: 4,
  topicStatus: {
    tagged: share(2),
    lowConfidence: share(1),
    unclassified: share(1),
  },
  topics: [
    { key: "politics.executive", name: "대통령실·행정부", share: share(2) },
  ],
  topicCategories: [{ key: "politics", name: "정치", share: share(2) }],
  regions: [{ key: "domestic", name: "국내", share: share(1) }],
  formats: [{ key: "straight", name: "스트레이트", share: share(4) }],
  topicsPerArticle: [],
  regionsPerArticle: [],
  topicProbabilities: {
    storeThreshold: 0.1,
    storeThresholdChanged: false,
    buckets: [],
  },
  regionProbabilities: {
    storeThreshold: 0.1,
    storeThresholdChanged: false,
    buckets: [],
  },
  formatProbabilities: {
    storeThreshold: null,
    storeThresholdChanged: false,
    buckets: [],
  },
  importance: [],
  importanceMissing: 0,
  inputTokens: { mean: null, min: null, max: null, buckets: [] },
};

export function row(
  id: number,
  title: string | null,
): Schemas["EvaluationRowResponse"] {
  return {
    id,
    articleId: id + 100,
    evaluatedAt: "2026-11-01T00:00:00Z",
    topicStatus: "TAGGED",
    topics: [
      { key: "politics.executive", name: "대통령실·행정부", probability: 0.8 },
    ],
    topicCategories: [{ key: "politics", name: "정치" }],
    regions: [],
    format: { key: "straight", name: "스트레이트", probability: 0.7 },
    importance: 3,
    article: title === null ? null : { title, sources: ["한겨레", "경향신문"] },
  };
}

export function detail(
  id: number,
  canonicalUrl = "https://news.example/a/1",
): Schemas["EvaluationDetailResponse"] {
  return {
    id,
    articleId: id + 100,
    version: 7,
    versionNumber: 2,
    evaluator: "jev",
    modelVersion: "jev-1.14.0",
    evaluatedAt: "2026-11-01T00:00:00Z",
    inputTokens: 1234,
    topicThresholds: { kind: "TOPIC", tagThreshold: 0.5, storeThreshold: 0.1 },
    regionThresholds: {
      kind: "REGION",
      tagThreshold: 0.5,
      storeThreshold: 0.1,
    },
    topicStatus: "TAGGED",
    topics: [
      {
        key: "politics.executive",
        name: "대통령실·행정부",
        probability: 0.8,
        attached: true,
      },
      {
        key: "politics.legislature",
        name: "국회·정당·선거",
        probability: 0.4,
        attached: false,
      },
    ],
    topicCategories: [{ key: "politics", name: "정치" }],
    regions: [],
    formats: [{ key: "straight", name: "스트레이트", probability: 0.7 }],
    format: "straight",
    importanceLevels: [{ key: "3", name: "여러 분야", probability: 1 }],
    importance: 3,
    article: {
      title: `기사 ${id}`,
      summary: "<b>요약</b> 원문",
      canonicalUrl,
      entries: [
        { sourceName: "한겨레", section: "정치", categories: ["국회"] },
      ],
    },
  };
}

/** 화면 하나를 메모리 라우터에 올린다 — 받은 요청은 server.requests */
export function renderAdminRoutes(
  routes: RouteObject[],
  initialEntry: string,
  handlers: Record<string, Handler>,
): {
  server: ReturnType<typeof fakeServer>;
  router: ReturnType<typeof createMemoryRouter>;
} {
  const server = fakeServer(handlers);
  const client = createAdminClient({
    baseUrl: TEST_BASE_URL,
    fetch: server.fetch,
    token: () => "token",
  });
  const router = createMemoryRouter(routes, { initialEntries: [initialEntry] });
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <AdminSessionContext.Provider value={client}>
        <RouterProvider router={router} />
      </AdminSessionContext.Provider>
    </QueryClientProvider>,
  );
  return { server, router };
}

/** 화면 하나만 그린다 — 라우터가 필요 없는 부품 시험 */
export function renderAlone(element: ReactNode) {
  return renderAdminRoutes([{ path: "/", element }], "/", {});
}
