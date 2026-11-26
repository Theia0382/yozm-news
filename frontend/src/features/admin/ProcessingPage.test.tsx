import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { describe, expect, it } from "vitest";
import { createAdminClient, type Schemas } from "@/features/admin/adminClient";
import { AdminSessionContext } from "@/features/admin/adminSession";
import { ProcessingPage } from "@/features/admin/ProcessingPage";
import {
  fakeServer,
  type Handler,
  json,
  problem,
  TEST_BASE_URL,
} from "@/test/fakeServer";

const PROCESSING_PATH = "GET /api/admin/processing";

const none = (
  round: string,
  waiting = 0,
  deferred = 0,
): Schemas["ProcessingRoundResponse"] => ({
  round,
  waiting,
  deferred,
  recentHandled: 0,
  recentRuns: 0,
  lastRun: null,
});

const PROCESSING: Schemas["ProcessingResponse"] = {
  rounds: [
    none("event-features", 3),
    {
      round: "event-assignment",
      waiting: 40,
      deferred: 2,
      recentHandled: 96,
      recentRuns: 3,
      lastRun: {
        startedAt: "2026-11-26T12:00:00Z",
        endedAt: "2026-11-26T12:05:02Z",
        batches: 11,
        handled: 352,
        endedBy: "BUDGET",
      },
    },
    none("event-rejudgement"),
    none("event-merge"),
    none("storyline-linking"),
    {
      round: "storyline-relation",
      waiting: 0,
      deferred: 0,
      recentHandled: 0,
      recentRuns: 1,
      lastRun: {
        startedAt: "2026-11-26T12:06:00Z",
        endedAt: "2026-11-26T12:06:00Z",
        batches: 1,
        handled: 0,
        endedBy: "DRAINED",
      },
    },
    none("tag-evaluation", 5, 5),
  ],
};

function renderPage(handler: Handler = () => json(PROCESSING)) {
  const server = fakeServer({ [PROCESSING_PATH]: handler });
  const client = createAdminClient({
    baseUrl: TEST_BASE_URL,
    fetch: server.fetch,
  });
  const router = createMemoryRouter([
    { path: "/", element: <ProcessingPage /> },
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

/** 표의 몸 행마다 칸 글자 — 머리 행은 뺀다 */
async function bodyRows(): Promise<string[][]> {
  const table = await screen.findByRole("table", { name: "회차" });
  const rows = within(table).getAllByRole("row").slice(1);
  return rows.map((row) =>
    within(row)
      .getAllByRole("cell")
      .map((cell) => cell.textContent ?? ""),
  );
}

describe("ProcessingPage", () => {
  it("회차 일곱을 받은 순서대로 한국어 이름 · 대기 · 미룸 · 최근 1시간 · 마지막 회차로 보인다", async () => {
    renderPage();

    expect(await bodyRows()).toEqual([
      ["기사 표현", "3", "0", "0", "0", "기록 없음"],
      [
        "사건 배정",
        "40",
        "2",
        "96",
        "3",
        "2026. 11. 26. 오후 9:05 GMT+9 · 시간을 다 씀",
      ],
      ["사건 재판정", "0", "0", "0", "0", "기록 없음"],
      ["사건 병합", "0", "0", "0", "0", "기록 없음"],
      ["흐름 잇기", "0", "0", "0", "0", "기록 없음"],
      [
        "흐름 관계",
        "0",
        "0",
        "0",
        "1",
        "2026. 11. 26. 오후 9:06 GMT+9 · 가져올 대기 없음",
      ],
      ["태그 평가", "5", "5", "0", "0", "기록 없음"],
    ]);
    expect(screen.getByRole("columnheader", { name: "미룸" })).toHaveAttribute(
      "title",
      "가져올 시각이 아직 오지 않은 대기(일시 실패 · 정착 시간)",
    );
    expect(
      screen.getByText(
        "대기가 밀리면 회차가 쉬지 않고 처리합니다. 같은 일의 Jev 비용은 같고 시간당 지출이 커집니다.",
      ),
    ).toBeInTheDocument();
  });

  it("끝난 까닭 다섯을 모두 표기하고 모르는 회차 이름은 그대로 보인다", async () => {
    const last = (
      endedBy: Schemas["ProcessingLastRunResponse"]["endedBy"],
    ) => ({
      startedAt: "2026-11-26T12:00:00Z",
      endedAt: "2026-11-26T12:00:00Z",
      batches: 1,
      handled: 0,
      endedBy,
    });
    renderPage(() =>
      json({
        rounds: [
          { ...none("a-round"), lastRun: last("DRAINED") },
          { ...none("b-round"), lastRun: last("BUDGET") },
          { ...none("c-round"), lastRun: last("BACKED_OFF") },
          { ...none("d-round"), lastRun: last("STOPPED") },
          { ...none("e-round"), lastRun: last("FAILED") },
        ],
      }),
    );

    expect((await bodyRows()).map((row) => [row[0], row[5]])).toEqual([
      ["a-round", "2026. 11. 26. 오후 9:00 GMT+9 · 가져올 대기 없음"],
      ["b-round", "2026. 11. 26. 오후 9:00 GMT+9 · 시간을 다 씀"],
      ["c-round", "2026. 11. 26. 오후 9:00 GMT+9 · 일시 실패로 쉼"],
      ["d-round", "2026. 11. 26. 오후 9:00 GMT+9 · 멈춤"],
      ["e-round", "2026. 11. 26. 오후 9:00 GMT+9 · 오류"],
    ]);
  });

  it("읽지 못하면 까닭을 알리고 다시 시도하면 다시 부른다", async () => {
    const server = renderPage(() => problem(500, "서버 오류"));

    expect(await screen.findByRole("alert")).toHaveTextContent("서버 오류");
    server.handlers[PROCESSING_PATH] = () => json(PROCESSING);
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(await bodyRows()).toHaveLength(7);
    expect(
      server.requests.filter(
        (request) =>
          request.method === "GET" && request.path === "/api/admin/processing",
      ),
    ).toHaveLength(2);
  });
});
