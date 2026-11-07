import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { Schemas } from "@/features/admin/adminClient";
import { json } from "@/features/admin/adminTestServer";
import { ARTICLE } from "@/features/admin/eventTestKit";
import { EventJudgementsPage } from "@/features/admin/EventJudgementsPage";
import { renderAdminRoutes } from "@/features/admin/reviewTestKit";

const LIST_PATH = "GET /api/admin/event-judgements";

const CRITERIA: Schemas["AssignmentCriteriaResponse"] = {
  windowSeconds: 172800,
  upper: 0.85,
  lower: 0.5,
  store: 0.35,
  probability: 0.6,
};

function judgement(
  id: number,
  changes: Partial<Schemas["EventJudgementRowResponse"]>,
): Schemas["EventJudgementRowResponse"] {
  return {
    id,
    judgedAt: "2026-11-05T00:00:00Z",
    articleId: 41,
    article: ARTICLE,
    candidates: [{ eventId: 12, probability: 0.83 }],
    inputTokens: 1812,
    modelVersion: "jev-1.14.0",
    use: "UNUSED",
    assignedEventId: null,
    decidedBy: null,
    mergePairs: [],
    ...changes,
  };
}

function renderPage(pages: unknown[]) {
  let call = 0;
  return renderAdminRoutes(
    [{ path: "/admin/event-judgements", element: <EventJudgementsPage /> }],
    "/admin/event-judgements?from=2026-11-01&to=2026-11-07",
    { [LIST_PATH]: () => json(pages[Math.min(call++, pages.length - 1)]) },
  ).server;
}

describe("EventJudgementsPage", () => {
  it("배정 기준을 읽기 전용으로, 판정마다 후보 · 확률 · 지금 결과 셋 · 입력 토큰을 보인다", async () => {
    const server = renderPage([
      {
        judgements: [
          judgement(103, {
            candidates: [
              { eventId: 12, probability: 0.83 },
              { eventId: 15, probability: 0.07 },
            ],
            use: "ASSIGNMENT",
            assignedEventId: 12,
            decidedBy: "EVALUATOR",
          }),
          judgement(102, {
            articleId: 42,
            article: null,
            use: "MERGE",
            mergePairs: [
              { low: 11, high: 15, probability: 0.66, survivor: 11 },
              { low: 11, high: 18, probability: 0.21, survivor: null },
            ],
          }),
          judgement(101, {}),
        ],
        next: null,
        criteria: CRITERIA,
        maxDays: 31,
      },
    ]);

    const criteria = await screen.findByRole("region", {
      name: "사건 배정 기준",
    });
    expect(criteria).toHaveTextContent(
      "시간 창 48시간 · 위 기준 0.85 · 아래 기준 0.5 · 저장 기준 0.35 · 확률 기준 0.6",
    );
    expect(within(criteria).queryByRole("textbox")).toBeNull();
    const rows = within(
      screen.getByRole("table", { name: "판정 기록" }),
    ).getAllByRole("row");
    const assignment = within(rows[1]!).getAllByRole("cell");
    expect(assignment[1]).toHaveTextContent("국회 본회의 통과");
    expect(
      within(assignment[2]!).getByRole("link", { name: "사건 12" }),
    ).toHaveAttribute("href", "/admin/events/12");
    expect(assignment[2]).toHaveTextContent("사건 12 0.83");
    expect(assignment[2]).toHaveTextContent("사건 15 0.07");
    expect(assignment[3]).toHaveTextContent(/^배정 — 사건 12 \(Jev\)$/);
    expect(assignment[4]).toHaveTextContent(/^1,812$/);
    const merge = within(rows[2]!).getAllByRole("cell");
    expect(merge[1]).toHaveTextContent(/^지운 기사 42$/);
    expect(merge[3]).toHaveTextContent("사건 11 · 15 0.66 — 합쳐짐(사건 11)");
    expect(merge[3]).toHaveTextContent("사건 11 · 18 0.21 — 합쳐지지 않음");
    expect(within(rows[3]!).getAllByRole("cell")[3]).toHaveTextContent(
      /^반영 안 됨$/,
    );
    const [request] = server.requests
      .filter((r) => r.path === "/api/admin/event-judgements")
      .map((r) => new URLSearchParams(r.search));
    expect(request?.get("from")).toBe("2026-11-01");
    expect(request?.has("afterId")).toBe(false);
  });

  it("더 보기가 받은 커서로 다음 쪽을 이어 받는다", async () => {
    const server = renderPage([
      {
        judgements: [judgement(103, {})],
        next: { judgedAt: "2026-11-05T00:00:00Z", id: 103 },
        criteria: CRITERIA,
        maxDays: 31,
      },
      {
        judgements: [judgement(99, { articleId: 7, article: null })],
        next: null,
        criteria: CRITERIA,
        maxDays: 31,
      },
    ]);

    await screen.findByRole("table", { name: "판정 기록" });
    await userEvent.click(screen.getByRole("button", { name: "더 보기" }));

    expect(await screen.findByText("지운 기사 7")).toBeInTheDocument();
    const second = server.requests.filter(
      (r) => r.path === "/api/admin/event-judgements",
    )[1];
    const query = new URLSearchParams(second?.search);
    expect(query.get("afterJudgedAt")).toBe("2026-11-05T00:00:00Z");
    expect(query.get("afterId")).toBe("103");
  });

  it("판정 기록이 없으면 알린다", async () => {
    renderPage([
      { judgements: [], next: null, criteria: CRITERIA, maxDays: 31 },
    ]);

    expect(
      await screen.findByText("이 기간에 판정 기록이 없다"),
    ).toBeInTheDocument();
  });
});
