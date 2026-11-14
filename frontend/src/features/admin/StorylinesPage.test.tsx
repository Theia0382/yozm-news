import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { json, problem } from "@/test/fakeServer";
import { storylineList, storylineRow } from "@/features/admin/eventTestKit";
import { renderAdminRoutes } from "@/features/admin/reviewTestKit";
import { StorylinesPage } from "@/features/admin/StorylinesPage";

const LIST_PATH = "GET /api/admin/storylines";

function renderPage(
  pages: unknown[],
  entry = "/admin/storylines?from=2026-11-01&to=2026-11-07",
) {
  let call = 0;
  return renderAdminRoutes(
    [{ path: "/admin/storylines", element: <StorylinesPage /> }],
    entry,
    {
      [LIST_PATH]: () => json(pages[Math.min(call++, pages.length - 1)]),
    },
  ).server;
}

function listRequests(server: ReturnType<typeof renderPage>) {
  return server.requests
    .filter((request) => request.path === "/api/admin/storylines")
    .map((request) => new URLSearchParams(request.search));
}

describe("StorylinesPage", () => {
  it("주소의 기간으로 스토리라인을 받아 상태 · 상위 · 수 · 기간 · 큰 여부 · 대표 기사를 열한 칸으로 보인다", async () => {
    const server = renderPage([
      storylineList([
        storylineRow(7),
        storylineRow(8, {
          state: "QUIET",
          parentId: 7,
          eventCount: 2,
          articleCount: 4,
          periodArticles: 1,
          periodSources: 1,
          importance: null,
          big: false,
          representativeArticleId: null,
          representative: null,
        }),
      ]),
    ]);

    const table = await screen.findByRole("table", { name: "스토리라인" });
    const rows = within(table).getAllByRole("row");
    expect(within(rows[0]!).getAllByRole("columnheader")).toHaveLength(11);
    expect(within(rows[0]!).getAllByRole("columnheader")[5]).toHaveTextContent(
      /^보도 강도\(기간\)$/,
    );
    const first = within(rows[1]!).getAllByRole("cell");
    expect(first).toHaveLength(11);
    expect(
      within(first[0]!).getByRole("link", { name: "스토리라인 7" }),
    ).toHaveAttribute("href", "/admin/storylines/7");
    expect(first[1]).toHaveTextContent(/^진행$/);
    expect(first[2]).toHaveTextContent(/^$/);
    expect(first[3]).toHaveTextContent(/^3$/);
    expect(first[4]).toHaveTextContent(/^4 \/ 12$/);
    expect(first[5]).toHaveTextContent(/^18\.75$/);
    expect(first[6]).toHaveTextContent(/^3$/);
    expect(first[7]).toHaveTextContent(/^3\.75$/);
    expect(first[9]).toHaveTextContent(/^큰 스토리라인$/);
    expect(first[10]).toHaveTextContent("국회 본회의 통과");
    const second = within(rows[2]!).getAllByRole("cell");
    expect(second[1]).toHaveTextContent(/^잠잠$/);
    expect(
      within(second[2]!).getByRole("link", { name: "스토리라인 7" }),
    ).toHaveAttribute("href", "/admin/storylines/7");
    expect(second[7]).toHaveTextContent(/^없음$/);
    expect(second[9]).toHaveTextContent(/^$/);
    expect(second[10]).toHaveTextContent(/^$/);
    const overview = screen.getByLabelText("기간 개요");
    expect(overview).toHaveTextContent("2건 · 기사 5건");
    expect(overview).toHaveTextContent("창 3일 · 기준 점수 0.62");
    expect(overview).toHaveTextContent(
      "확률 0.6 · 한 사안 0.2 · 사건 수 3 이상 · 후보 점수 0.88 · 창 14일",
    );
    const [request] = listRequests(server);
    expect(request?.get("from")).toBe("2026-11-01");
    expect(request?.get("to")).toBe("2026-11-07");
    expect(request?.has("afterId")).toBe(false);
    expect(screen.queryByRole("button", { name: "더 보기" })).toBeNull();
  });

  it("더 보기가 받은 커서로 다음 쪽을 이어 받는다", async () => {
    const server = renderPage([
      storylineList([storylineRow(7)], {
        lastArticleAt: "2026-11-06T00:00:00Z",
        id: 7,
      }),
      storylineList([storylineRow(5)]),
    ]);

    await screen.findByRole("link", { name: "스토리라인 7" });
    await userEvent.click(screen.getByRole("button", { name: "더 보기" }));

    expect(
      await screen.findByRole("link", { name: "스토리라인 5" }),
    ).toBeInTheDocument();
    const [, second] = listRequests(server);
    expect(second?.get("afterLastArticleAt")).toBe("2026-11-06T00:00:00Z");
    expect(second?.get("afterId")).toBe("7");
    expect(screen.queryByRole("button", { name: "더 보기" })).toBeNull();
  });

  it("기사가 있는 스토리라인이 없으면 알린다", async () => {
    renderPage([storylineList([])]);

    expect(
      await screen.findByText("이 기간에 기사가 있는 스토리라인이 없다"),
    ).toBeInTheDocument();
  });

  it("서버의 400 설명을 보인다", async () => {
    renderAdminRoutes(
      [{ path: "/admin/storylines", element: <StorylinesPage /> }],
      "/admin/storylines?from=2026-11-01&to=2026-12-02",
      { [LIST_PATH]: () => problem(400, "기간은 1일에서 31일까지다") },
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "기간은 1일에서 31일까지다",
    );
  });
});
