import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { json, problem } from "@/features/admin/adminTestServer";
import { eventRow } from "@/features/admin/eventTestKit";
import { EventsPage } from "@/features/admin/EventsPage";
import { renderAdminRoutes } from "@/features/admin/reviewTestKit";

const LIST_PATH = "GET /api/admin/events";

function renderPage(
  pages: unknown[],
  entry = "/admin/events?from=2026-11-01&to=2026-11-07",
) {
  let call = 0;
  return renderAdminRoutes(
    [{ path: "/admin/events", element: <EventsPage /> }],
    entry,
    {
      [LIST_PATH]: () => json(pages[Math.min(call++, pages.length - 1)]),
    },
  ).server;
}

function listRequests(server: ReturnType<typeof renderPage>) {
  return server.requests
    .filter((request) => request.path === "/api/admin/events")
    .map((request) => new URLSearchParams(request.search));
}

describe("EventsPage", () => {
  it("주소의 기간으로 사건을 받아 상태 · 기간 / 전체 기사 수 · 소스 수 · 대표 기사를 여덟 칸으로 보인다", async () => {
    const server = renderPage([
      {
        events: [
          eventRow(7, { status: "CLOSED" }),
          eventRow(5, {
            articleCount: 1,
            periodArticles: 1,
            periodSources: 1,
            importance: null,
            representativeId: 42,
            representative: null,
          }),
        ],
        next: null,
        maxDays: 31,
      },
    ]);

    const table = await screen.findByRole("table", { name: "사건" });
    const rows = within(table).getAllByRole("row");
    const first = within(rows[1]!).getAllByRole("cell");
    expect(
      within(first[0]!).getByRole("link", { name: "사건 7" }),
    ).toHaveAttribute("href", "/admin/events/7");
    expect(first[1]).toHaveTextContent(/^닫힘$/);
    expect(first[2]).toHaveTextContent(/^3 \/ 5$/);
    expect(first[3]).toHaveTextContent(/^2$/);
    expect(first[4]).toHaveTextContent(/^3\.5$/);
    expect(first).toHaveLength(8);
    expect(first[7]).toHaveTextContent("국회 본회의 통과");
    expect(first[7]).toHaveTextContent("한겨레 · 경향신문");
    expect(within(rows[0]!).getAllByRole("columnheader")).toHaveLength(8);
    const second = within(rows[2]!).getAllByRole("cell");
    expect(second[1]).toHaveTextContent(/^열림$/);
    expect(second[4]).toHaveTextContent(/^없음$/);
    expect(second[7]).toHaveTextContent(/^지운 기사 42$/);
    const [request] = listRequests(server);
    expect(request?.get("from")).toBe("2026-11-01");
    expect(request?.get("to")).toBe("2026-11-07");
    expect(request?.has("afterId")).toBe(false);
    expect(screen.queryByRole("button", { name: "더 보기" })).toBeNull();
  });

  it("더 보기가 받은 커서로 다음 쪽을 이어 받는다", async () => {
    const server = renderPage([
      {
        events: [eventRow(7)],
        next: { lastArticleAt: "2026-11-06T00:00:00Z", id: 7 },
        maxDays: 31,
      },
      { events: [eventRow(5)], next: null, maxDays: 31 },
    ]);

    await screen.findByRole("link", { name: "사건 7" });
    await userEvent.click(screen.getByRole("button", { name: "더 보기" }));

    expect(
      await screen.findByRole("link", { name: "사건 5" }),
    ).toBeInTheDocument();
    const [, second] = listRequests(server);
    expect(second?.get("afterLastArticleAt")).toBe("2026-11-06T00:00:00Z");
    expect(second?.get("afterId")).toBe("7");
    expect(screen.queryByRole("button", { name: "더 보기" })).toBeNull();
  });

  it("기사가 있는 사건이 없으면 알린다", async () => {
    renderPage([{ events: [], next: null, maxDays: 31 }]);

    expect(
      await screen.findByText("이 기간에 기사가 있는 사건이 없다"),
    ).toBeInTheDocument();
  });

  it("서버의 400 설명을 보인다", async () => {
    renderAdminRoutes(
      [{ path: "/admin/events", element: <EventsPage /> }],
      "/admin/events?from=2026-11-01&to=2026-12-02",
      { [LIST_PATH]: () => problem(400, "기간은 1일에서 31일까지다") },
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "기간은 1일에서 31일까지다",
    );
  });
});
