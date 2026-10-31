import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { describe, expect, it } from "vitest";
import { createAdminClient, type Schemas } from "@/features/admin/adminClient";
import { AdminSessionContext } from "@/features/admin/adminSession";
import {
  fakeServer,
  type Handler,
  json,
  noContent,
  problem,
  TEST_BASE_URL,
} from "@/features/admin/adminTestServer";
import { SourcesPage } from "@/features/admin/SourcesPage";

const PRESETS: Schemas["PresetResponse"][] = [
  {
    key: "multi",
    name: "멀티일보",
    homepage: "https://multi.example",
    termsReviewedAt: "2026-10-20",
    feedCount: 2,
    sourceId: 3,
  },
  {
    key: "solo",
    name: "솔로뉴스",
    homepage: "https://solo.example",
    termsReviewedAt: "2026-10-21",
    feedCount: 1,
    sourceId: null,
  },
];

const SOURCES: Schemas["SourceListResponse"] = {
  minimumPollIntervalMinutes: 15,
  sources: [
    {
      id: 3,
      presetKey: "multi",
      enabled: true,
      pollIntervalMinutes: null,
      profileState: "AVAILABLE",
      name: "멀티일보",
      homepage: "https://multi.example",
      termsReviewedAt: "2026-10-20",
      feeds: [
        {
          id: 11,
          feedKey: "one",
          enabled: false,
          pollIntervalMinutes: null,
          inProfile: true,
          url: "https://multi.example/rss/one.xml",
          section: "하나",
          effectivePollIntervalMinutes: 15,
        },
        {
          id: 12,
          feedKey: "gone",
          enabled: false,
          pollIntervalMinutes: null,
          inProfile: false,
          url: null,
          section: null,
          effectivePollIntervalMinutes: null,
        },
      ],
    },
    {
      id: 4,
      presetKey: "vanished",
      enabled: false,
      pollIntervalMinutes: null,
      profileState: "PRESET_MISSING",
      name: null,
      homepage: null,
      termsReviewedAt: null,
      feeds: [],
    },
  ],
};

function renderPage(extra: Record<string, Handler> = {}) {
  const server = fakeServer({
    "GET /api/admin/presets": () => json(PRESETS),
    "GET /api/admin/sources": () => json(SOURCES),
    ...extra,
  });
  const client = createAdminClient({
    baseUrl: TEST_BASE_URL,
    fetch: server.fetch,
    token: () => "token",
  });
  const router = createMemoryRouter([{ path: "/", element: <SourcesPage /> }]);
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
  return server;
}

function feedRow(key: string): HTMLElement {
  const row = screen.getByText(key).closest("li");
  if (!row) {
    throw new Error(`피드 ${key} 의 줄이 없다`);
  }
  return row;
}

describe("SourcesPage", () => {
  it("프리셋은 등록 여부를, 가리킬 곳을 잃은 소스 · 피드는 그 사실을 보인다", async () => {
    renderPage();

    expect(await screen.findByText("등록됨 (소스 3)")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "등록" })).toBeInTheDocument();
    expect(await screen.findByText("one")).toBeInTheDocument();
    expect(
      within(feedRow("one")).getByText(
        "https://multi.example/rss/one.xml · 실제 주기 15분",
      ),
    ).toBeInTheDocument();
    expect(
      within(feedRow("gone")).getByText(
        "프로필에 없는 피드입니다(수집하지 않습니다)",
      ),
    ).toBeInTheDocument();
    const vanished = screen.getByRole("heading", { name: /^소스 4/ });
    expect(vanished).toHaveTextContent("소스 4 프리셋 vanished");
    expect(
      screen.getByText("참조하던 프리셋이 사라졌습니다"),
    ).toBeInTheDocument();
  });

  it("피드를 켜는 동안 버튼을 막고, 끝나면 결과를 보인다", async () => {
    const user = userEvent.setup();
    let answer: (response: Response) => void = () => {};
    const server = renderPage({
      "POST /api/admin/feeds/11/enable": () =>
        new Promise<Response>((resolve) => {
          answer = resolve;
        }),
    });

    await screen.findByText("one");
    await user.click(
      within(feedRow("one")).getByRole("button", { name: "켜기" }),
    );

    const pending = within(feedRow("one")).getByRole("button", {
      name: "받아서 확인하는 중…",
    });
    expect(pending).toBeDisabled();

    answer(
      json({
        result: "REFUSED",
        preview: null,
        shortfalls: [],
        refusal: "FEED_NOT_IN_PROFILE",
        failure: null,
      }),
    );

    expect(
      await within(feedRow("one")).findByText(
        "켜지 않았습니다 — 프로필에 이 피드가 없습니다",
      ),
    ).toBeInTheDocument();
    expect(
      server.requests.filter((r) => r.path === "/api/admin/feeds/11/enable"),
    ).toHaveLength(1);
  });

  it("하한보다 짧은 주기는 서버의 거부 문구를 보이고, 비우면 설정을 지운다", async () => {
    const user = userEvent.setup();
    const server = renderPage({
      "PUT /api/admin/feeds/11/poll-interval": async (request) =>
        (await request.json()).minutes === 10
          ? problem(400, "수집 주기는 15분 이상이다")
          : noContent(),
    });
    await screen.findByText("one");
    const row = feedRow("one");
    const input = within(row).getByLabelText("피드 수집 주기(분)");

    await user.type(input, "10");
    await user.click(within(row).getByRole("button", { name: "저장" }));

    expect(
      await within(row).findByText("수집 주기는 15분 이상이다"),
    ).toBeInTheDocument();
    expect(
      within(row).getByText("15분 이상, 비우면 설정을 지웁니다"),
    ).toBeInTheDocument();

    await user.clear(input);
    await user.click(within(row).getByRole("button", { name: "저장" }));

    await waitFor(() =>
      expect(
        server.requests
          .filter((r) => r.path === "/api/admin/feeds/11/poll-interval")
          .map((r) => r.body),
      ).toEqual(['{"minutes":10}', '{"minutes":null}']),
    );
  });
});
