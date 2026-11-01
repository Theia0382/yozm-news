import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
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
  problem,
  TEST_BASE_URL,
} from "@/features/admin/adminTestServer";
import { CollectionPage } from "@/features/admin/CollectionPage";

type EntryMetrics = Schemas["EntryMetricsResponse"];

const FROM = "2026-10-30T03:00:00Z";
const MIDDLE = "2026-10-31T03:00:00Z";
const TO = "2026-11-01T03:00:00Z";

const NO_ENTRIES: EntryMetrics = {
  entries: 0,
  duplicates: 0,
  titleMatches: 0,
  estimated: 0,
  guidReuses: 0,
  duplicateRate: null,
  estimatedRate: null,
  titleMatchRate: null,
};

const FIRST_BUCKET: EntryMetrics = {
  entries: 12,
  duplicates: 9,
  titleMatches: 3,
  estimated: 6,
  guidReuses: 1,
  duplicateRate: 0.75,
  estimatedRate: 0.5,
  titleMatchRate: 0.25,
};

const SECOND_BUCKET: EntryMetrics = {
  entries: 988,
  duplicates: 747,
  titleMatches: 7,
  estimated: 4,
  guidReuses: 0,
  duplicateRate: 0.756,
  estimatedRate: 0.004,
  titleMatchRate: 0.007,
};

/** 기준값 · 간격은 서버 기본값과 다르게 둔다 — 화면이 서버 값을 쓰는지 본다 */
const STATUS: Schemas["CollectionStatusResponse"] = {
  from: FROM,
  to: TO,
  criteria: {
    degradedAfterFailures: 6,
    degradedAfterPermanentFailures: 3,
    staleAfterHours: 48,
    recheckIntervalMinutes: 30,
  },
  sources: [
    {
      id: 3,
      name: "멀티일보",
      presetKey: "multi",
      enabled: true,
      profileState: "AVAILABLE",
      total: {
        entries: 1000,
        duplicates: 756,
        titleMatches: 10,
        estimated: 10,
        guidReuses: 1,
        duplicateRate: 0.756,
        estimatedRate: 0.01,
        titleMatchRate: 0.01,
      },
      buckets: [
        { from: FROM, to: MIDDLE, metrics: FIRST_BUCKET },
        { from: MIDDLE, to: TO, metrics: SECOND_BUCKET },
      ],
      feeds: [
        {
          id: 11,
          feedKey: "one",
          section: "하나",
          enabled: true,
          inProfile: true,
          status: "DEGRADED",
          statusChangedAt: "2026-10-31T04:30:00Z",
          lastSuccessAt: "2026-10-30T22:00:00Z",
          consecutiveFailures: 6,
          consecutivePermanentFailures: 2,
          lastFailure: {
            reason: "ACCESS_DENIED",
            permanence: "PERMANENT",
            message: "서버가 요청을 거부했다",
            httpStatus: 403,
            at: "2026-10-31T04:00:00Z",
          },
          lastNewItemAt: "2026-10-30T21:00:00Z",
          latestPublishedAt: "2026-10-30T20:30:00Z",
          total: {
            entries: 1000,
            duplicates: 756,
            titleMatches: 10,
            estimated: 10,
            guidReuses: 1,
            duplicateRate: 0.756,
            estimatedRate: 0.01,
            titleMatchRate: 0.01,
          },
          buckets: [
            { from: FROM, to: MIDDLE, metrics: FIRST_BUCKET },
            { from: MIDDLE, to: TO, metrics: SECOND_BUCKET },
          ],
        },
        {
          id: 12,
          feedKey: "gone",
          section: null,
          enabled: false,
          inProfile: false,
          status: "ACTIVE",
          statusChangedAt: null,
          lastSuccessAt: null,
          consecutiveFailures: 0,
          consecutivePermanentFailures: 0,
          lastFailure: null,
          lastNewItemAt: null,
          latestPublishedAt: null,
          total: NO_ENTRIES,
          buckets: [
            { from: FROM, to: MIDDLE, metrics: NO_ENTRIES },
            { from: MIDDLE, to: TO, metrics: NO_ENTRIES },
          ],
        },
      ],
    },
    {
      id: 4,
      name: null,
      presetKey: null,
      enabled: false,
      profileState: "INVALID",
      total: NO_ENTRIES,
      buckets: [
        { from: FROM, to: MIDDLE, metrics: NO_ENTRIES },
        { from: MIDDLE, to: TO, metrics: NO_ENTRIES },
      ],
      feeds: [],
    },
  ],
};

const FIRST_PERIOD =
  "2026. 10. 30. 오후 12:00 GMT+9 ~ 2026. 10. 31. 오후 12:00 GMT+9";

function renderPage(
  handler: Handler = () => json(STATUS),
): ReturnType<typeof fakeServer> {
  const server = fakeServer({ "GET /api/admin/collection-status": handler });
  const client = createAdminClient({
    baseUrl: TEST_BASE_URL,
    fetch: server.fetch,
    token: () => "token",
  });
  const router = createMemoryRouter([
    { path: "/", element: <CollectionPage /> },
  ]);
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

/** 줄 머리 이름으로 찾은 표의 줄 */
function row(name: string): HTMLElement {
  const header = screen.getByRole("rowheader", {
    name: new RegExp(`^${name}`),
  });
  const tr = header.closest("tr");
  if (!tr) {
    throw new Error(`${name} 의 줄이 없다`);
  }
  return tr;
}

describe("CollectionPage", () => {
  it("집계 기간과 지표 정의, 서버가 준 기준값 · 간격으로 상태 정의를 보인다", async () => {
    renderPage();

    expect(
      await screen.findByText(
        /집계 기간: 2026\. 10\. 30\. 오후 12:00 GMT\+9 ~ 2026\. 11\. 01\. 오후 12:00 GMT\+9/,
      ),
    ).toBeInTheDocument();
    const definitions = screen.getByRole("region", {
      name: "지표와 상태의 정의",
    });
    expect(definitions).toHaveTextContent(
      "같은 피드에 다시 실린 항목은 세지 않고, 기사 저장까지 처리된 항목만 센다",
    );
    expect(definitions).toHaveTextContent(
      "연속 실패 6회 이상이거나 그 가운데 영구 실패 3회 이상",
    );
    expect(definitions).toHaveTextContent(
      "새 항목의 가장 늦은 발행 시각이 2일 이상 지남",
    );
    expect(definitions).toHaveTextContent("상태는 30분마다 다시 판정한다");
  });

  it("피드마다 수집 상태와 지표를, 소스마다 합계를 보인다", async () => {
    renderPage();

    await screen.findByRole("heading", { name: /멀티일보/ });
    const failing = row("하나");
    expect(failing).toHaveTextContent("켜짐");
    expect(failing).toHaveTextContent("계속 실패");
    expect(failing).toHaveTextContent("2026. 10. 31. 오후 1:30 GMT+9부터");
    expect(failing).toHaveTextContent("2026. 10. 31. 오전 7:00 GMT+9");
    expect(failing).toHaveTextContent("6회 (영구 2회)");
    expect(failing).toHaveTextContent("접근 거부 · 영구적 · HTTP 403");
    expect(failing).toHaveTextContent("서버가 요청을 거부했다");
    expect(failing).toHaveTextContent("2026. 10. 31. 오후 1:00 GMT+9");
    expect(failing).toHaveTextContent("2026. 10. 31. 오전 6:00 GMT+9");
    expect(failing).toHaveTextContent("2026. 10. 31. 오전 5:30 GMT+9");
    expect(failing).toHaveTextContent("75.6%");
    expect(failing).toHaveTextContent("1.0%");

    const gone = row("gone");
    expect(gone).toHaveTextContent("꺼짐");
    expect(gone).toHaveTextContent("프로필에 없음");
    expect(gone).toHaveTextContent("정상");
    expect(gone).toHaveTextContent("바뀐 적 없음");
    expect(within(gone).getAllByText("없음").length).toBeGreaterThanOrEqual(3);

    const total = within(
      screen.getByRole("region", { name: "멀티일보" }),
    ).getByRole("rowheader", { name: "소스 합계" });
    expect(total.closest("tr")).toHaveTextContent("1000");
  });

  it("소스가 꺼지면 켜진 피드를 소스 꺼짐으로 보인다", async () => {
    renderPage(() =>
      json({
        ...STATUS,
        sources: STATUS.sources.map((source) =>
          source.id === 3 ? { ...source, enabled: false } : source,
        ),
      }),
    );

    await screen.findByRole("heading", { name: /멀티일보/ });
    expect(row("하나")).toHaveTextContent("소스 꺼짐");
    expect(row("gone")).not.toHaveTextContent("소스 꺼짐");
  });

  it("엔트리가 없는 소스의 비율은 없음이고 프로필 문제를 보인다", async () => {
    renderPage();

    const unnamed = await screen.findByRole("region", {
      name: "이름을 읽지 못한 소스",
    });
    expect(unnamed).toHaveTextContent("운영자 원문이 검증에 걸립니다");
    const total = within(unnamed)
      .getByRole("rowheader", { name: "소스 합계" })
      .closest("tr");
    expect(total).not.toBeNull();
    expect(within(total as HTMLElement).getAllByText("없음")).toHaveLength(3);
  });

  it("칸을 누르면 그 칸의 기간 · 개수 · 비율을 연다", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", {
        name: `하나 중복률 ${FIRST_PERIOD}: 75.0%`,
      }),
    );

    const detail = await screen.findByRole("dialog");
    expect(detail).toHaveTextContent(FIRST_PERIOD);
    expect(detail).toHaveTextContent("엔트리12건");
    expect(detail).toHaveTextContent("중복9건 (그중 제목 판정 3건)");
    expect(detail).toHaveTextContent("날짜 추정6건");
    expect(detail).toHaveTextContent("guid 재사용1건");
    expect(detail).toHaveTextContent("중복률75.0%");
    expect(detail).toHaveTextContent("날짜 추정 비율50.0%");
    expect(detail).toHaveTextContent("제목 판정 비율25.0%");
  });

  it("키보드로 칸을 골라 Enter 로 연다", async () => {
    const user = userEvent.setup();
    renderPage();

    const cell = await screen.findByRole("button", {
      name: `gone 날짜 추정 비율 ${FIRST_PERIOD}: 없음`,
    });
    cell.focus();
    await user.keyboard("{Enter}");

    const detail = await screen.findByRole("dialog");
    expect(detail).toHaveTextContent("엔트리0건");
    expect(detail).toHaveTextContent("중복률없음");
  });

  it("조회가 실패하면 그 이유를 알린다", async () => {
    renderPage(() => problem(500, "서버 오류가 났다"));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "서버 오류가 났다",
    );
  });
});
