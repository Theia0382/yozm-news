import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { json } from "@/test/fakeServer";
import { EvaluationListPage } from "@/features/admin/EvaluationListPage";
import {
  OVERVIEW,
  renderAdminRoutes,
  row,
  TAG_SETS,
} from "@/features/admin/reviewTestKit";

const LIST_PATH = "GET /api/admin/evaluations";

function renderPage(pages: unknown[]) {
  let call = 0;
  return renderAdminRoutes(
    [{ path: "/admin/evaluations", element: <EvaluationListPage /> }],
    "/admin/evaluations?from=2026-10-28&to=2026-11-03",
    {
      "GET /api/admin/tag-sets": () => json(TAG_SETS),
      "GET /api/admin/evaluation-overview": () => json(OVERVIEW),
      [LIST_PATH]: () => json(pages[Math.min(call++, pages.length - 1)]),
    },
  ).server;
}

const page = (rows: ReturnType<typeof row>[], next: number | null) => ({
  version: 7,
  from: "2026-10-27T15:00:00Z",
  to: "2026-11-03T15:00:00Z",
  rows,
  nextEvaluatedAt: next === null ? null : "2026-11-01T00:00:00Z",
  nextId: next,
});

function listRequests(server: ReturnType<typeof renderPage>) {
  return server.requests
    .filter((request) => request.path === "/api/admin/evaluations")
    .map((request) => new URLSearchParams(request.search));
}

describe("EvaluationListPage", () => {
  it("활성 버전 · 주소의 기간으로 목록을 받아 기사 제목 · 출처 · 판정을 보인다", async () => {
    const server = renderPage([
      page([row(9, "국회 본회의 통과"), row(8, null)], null),
    ]);

    const table = await screen.findByRole("table", { name: "평가 결과" });
    const link = within(table).getByRole("link", { name: "국회 본회의 통과" });
    expect(link).toHaveAttribute("href", "/admin/evaluations/9");
    expect(within(table).getByText("한겨레 · 경향신문")).toBeInTheDocument();
    expect(
      within(table).getByRole("link", { name: "지운 기사 108" }),
    ).toBeInTheDocument();
    const [request] = listRequests(server);
    expect(request?.get("version")).toBe("7");
    expect(request?.get("from")).toBe("2026-10-28");
    expect(request?.get("to")).toBe("2026-11-03");
    expect(request?.get("limit")).toBe("20");
    expect(request?.has("tagKind")).toBe(false);
    expect(screen.queryByRole("button", { name: "더 보기" })).toBeNull();
  });

  it("행의 확률 · 중요도 기대값을 셋째 자리까지 반올림해 보인다", async () => {
    const raw = row(9, "국회 본회의 통과");
    renderPage([
      page(
        [
          {
            ...raw,
            topics: [{ ...raw.topics[0]!, probability: 0.10885070239097819 }],
            importance: 2.9999999999999996,
          },
        ],
        null,
      ),
    ]);

    const table = await screen.findByRole("table", { name: "평가 결과" });
    const cells = within(within(table).getAllByRole("row")[1]!).getAllByRole(
      "cell",
    );
    expect(cells[3]).toHaveTextContent(/^대통령실·행정부 0\.109$/);
    // 대조 — 끝의 0 이 없는 값은 그대로
    expect(cells[5]).toHaveTextContent(/^스트레이트 0\.7$/);
    expect(cells[6]).toHaveTextContent(/^3$/);
    expect(table.textContent).not.toContain("0.10885070239097819");
    expect(table.textContent).not.toContain("2.9999999999999996");
  });

  it("거르기 선택지는 평가 현황의 키와 수이고, 고르면 그 거르기로 다시 받는다", async () => {
    const server = renderPage([page([row(9, "국회 본회의 통과")], null)]);
    await screen.findByRole("table", { name: "평가 결과" });

    await userEvent.selectOptions(
      await screen.findByLabelText("태그"),
      screen.getByRole("option", { name: "정치 (2)" }),
    );
    await userEvent.selectOptions(
      screen.getByLabelText("주제 판정"),
      screen.getByRole("option", { name: "low_confidence (1)" }),
    );

    const last = listRequests(server).at(-1);
    expect(last?.get("tagKind")).toBe("TOPIC");
    expect(last?.get("tag")).toBe("politics");
    expect(last?.get("topicStatus")).toBe("LOW_CONFIDENCE");
  });

  it("더 보기가 마지막 쪽의 커서로 이어 받는다", async () => {
    const server = renderPage([
      page([row(9, "첫 쪽")], 9),
      page([row(5, "둘째 쪽")], null),
    ]);
    await screen.findByText("첫 쪽");

    await userEvent.click(screen.getByRole("button", { name: "더 보기" }));

    expect(await screen.findByText("둘째 쪽")).toBeInTheDocument();
    const second = listRequests(server)[1];
    expect(second?.get("afterEvaluatedAt")).toBe("2026-11-01T00:00:00Z");
    expect(second?.get("afterId")).toBe("9");
    expect(screen.queryByRole("button", { name: "더 보기" })).toBeNull();
  });
});
