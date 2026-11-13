import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { json, problem } from "@/test/fakeServer";
import { ARTICLE } from "@/features/admin/eventTestKit";
import { EventDetailPage } from "@/features/admin/EventDetailPage";
import { renderAdminRoutes } from "@/features/admin/reviewTestKit";
import type { Schemas } from "@/features/admin/adminClient";

const DETAIL: Schemas["EventDetailResponse"] = {
  requestedId: 7,
  id: 7,
  status: "CLOSED",
  articleCount: 2,
  firstArticleAt: "2026-11-05T00:00:00Z",
  lastArticleAt: "2026-11-06T00:00:00Z",
  createdAt: "2026-11-05T00:00:00Z",
  representativeId: 41,
  representative: ARTICLE,
  articles: [
    {
      articleId: 41,
      article: ARTICLE,
      publishedAt: "2026-11-05T00:00:00Z",
      score: 0.91,
      decidedBy: "FORMULA",
      assignedAt: "2026-11-05T00:00:00Z",
    },
    {
      articleId: 42,
      article: null,
      publishedAt: "2026-11-06T00:00:00Z",
      score: null,
      decidedBy: "JUDGEMENT_FAILED",
      assignedAt: "2026-11-06T01:00:00Z",
    },
    {
      articleId: 43,
      article: { title: "본회의 이후", sources: [] },
      publishedAt: "2026-11-06T00:00:00Z",
      score: 0.6,
      decidedBy: "EVALUATOR",
      assignedAt: "2026-11-06T01:00:00Z",
    },
  ],
  mergedEvents: [
    {
      id: 3,
      articleCount: 2,
      firstArticleAt: "2026-11-05T00:00:00Z",
      lastArticleAt: "2026-11-05T00:00:00Z",
      createdAt: "2026-11-05T00:00:00Z",
      mergedAt: "2026-11-06T06:00:00Z",
      representativeId: 44,
      representative: { title: "기준금리 동결", sources: ["연합뉴스"] },
      probability: 0.74,
    },
    {
      id: 4,
      articleCount: 1,
      firstArticleAt: "2026-11-05T00:00:00Z",
      lastArticleAt: "2026-11-05T00:00:00Z",
      createdAt: "2026-11-05T00:00:00Z",
      mergedAt: "2026-11-06T07:00:00Z",
      representativeId: null,
      representative: null,
      probability: null,
    },
  ],
};

function renderPage(id: number, response: () => Response) {
  return renderAdminRoutes(
    [{ path: "/admin/events/:id", element: <EventDetailPage /> }],
    `/admin/events/${id}`,
    { [`GET /api/admin/events/${id}`]: response },
  ).server;
}

describe("EventDetailPage", () => {
  it("사건 값 · 대표 기사 · 기사의 점수와 판정 방식 · 합쳐진 사건을 보인다", async () => {
    renderPage(7, () => json(DETAIL));

    expect(
      await screen.findByRole("heading", { name: "사건 7" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("status")).toBeNull();
    const values = screen.getByLabelText("사건 값");
    expect(values).toHaveTextContent("닫힘");
    expect(values).toHaveTextContent("국회 본회의 통과");
    const articles = within(
      screen.getByRole("table", { name: "사건의 기사" }),
    ).getAllByRole("row");
    const first = within(articles[1]!).getAllByRole("cell");
    expect(first[0]).toHaveTextContent("한겨레 · 경향신문");
    expect(first[2]).toHaveTextContent(/^0\.91$/);
    expect(first[3]).toHaveTextContent(/^산식$/);
    const second = within(articles[2]!).getAllByRole("cell");
    expect(second[0]).toHaveTextContent(/^지운 기사 42$/);
    expect(second[2]).toHaveTextContent(/^없음$/);
    expect(second[3]).toHaveTextContent(/^판정 실패$/);
    expect(within(articles[3]!).getAllByRole("cell")[3]).toHaveTextContent(
      /^Jev$/,
    );
    const merged = within(
      screen.getByRole("table", { name: "합쳐진 사건" }),
    ).getAllByRole("row");
    expect(within(merged[1]!).getAllByRole("cell")[0]).toHaveTextContent(
      /^사건 3$/,
    );
    expect(within(merged[1]!).getAllByRole("cell")[1]).toHaveTextContent(
      /^기준금리 동결연합뉴스$/,
    );
    expect(within(merged[1]!).getAllByRole("cell")[6]).toHaveTextContent(
      /^0\.74$/,
    );
    expect(within(merged[2]!).getAllByRole("cell")[1]).toHaveTextContent(
      /^기록 없음$/,
    );
    expect(within(merged[2]!).getAllByRole("cell")[6]).toHaveTextContent(
      /^기록 없음$/,
    );
  });

  it("합쳐진 사건의 ID 로 열면 남는 사건을 보이고 알린다", async () => {
    renderPage(3, () => json({ ...DETAIL, requestedId: 3, mergedEvents: [] }));

    expect(
      await screen.findByRole("heading", { name: "사건 7" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "합쳐진 사건 3 대신 남는 사건을 보인다",
    );
    expect(
      screen.getByText("이 사건으로 합쳐진 사건이 없다"),
    ).toBeInTheDocument();
  });

  it("없는 사건은 서버의 설명을 보인다", async () => {
    renderPage(99, () => problem(404, "없는 사건: 99"));

    expect(await screen.findByRole("alert")).toHaveTextContent("없는 사건: 99");
  });
});
