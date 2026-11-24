import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { json, problem } from "@/test/fakeServer";
import { ArticleDetailPage } from "@/features/admin/ArticleDetailPage";
import { renderAdminRoutes } from "@/features/admin/reviewTestKit";
import type { Schemas } from "@/features/admin/adminClient";

const DETAIL: Schemas["ArticleReviewResponse"] = {
  id: 41,
  article: {
    title: "국회 본회의 통과",
    summary: "<b>요약</b> 원문",
    canonicalUrl: "https://news.example/a/41",
    publishedAt: "2026-11-05T00:00:00Z",
    entries: [
      { sourceName: "한겨레", section: "정치", categories: ["정치"] },
      { sourceName: null, section: null, categories: [] },
    ],
  },
  evaluations: [
    {
      id: 19,
      version: 2,
      versionNumber: 2,
      evaluatedAt: "2026-11-06T00:00:00Z",
      topicStatus: "LOW_CONFIDENCE",
      topics: [
        {
          key: "politics.legislature",
          name: "국회·정당·선거",
          probability: 0.4,
        },
      ],
      topicCategories: [{ key: "politics", name: "정치" }],
      regions: [],
      format: { key: "straight", name: "스트레이트", probability: 0.7 },
      importance: null,
    },
    {
      id: 9,
      version: 1,
      versionNumber: 1,
      evaluatedAt: "2026-11-05T00:00:00Z",
      topicStatus: "TAGGED",
      topics: [
        {
          key: "politics.legislature",
          name: "국회·정당·선거",
          probability: 0.8,
        },
      ],
      topicCategories: [{ key: "politics", name: "정치" }],
      regions: [{ key: "domestic", name: "국내", probability: 0.9 }],
      format: { key: "straight", name: "스트레이트", probability: 0.7 },
      importance: 3.25,
    },
  ],
  assignment: {
    eventId: 7,
    event: {
      status: "OPEN",
      articleCount: 4,
      firstArticleAt: "2026-11-05T00:00:00Z",
      lastArticleAt: "2026-11-06T00:00:00Z",
    },
    publishedAt: "2026-11-05T00:00:00Z",
    score: 0.62,
    decidedBy: "EVALUATOR",
    opened: false,
    criteria: {
      windowSeconds: 259200,
      upper: 0.8,
      lower: 0.55,
      store: 0.4,
      probability: 0.5,
    },
    assignedAt: "2026-11-05T00:10:00Z",
    candidates: [
      { eventId: 7, currentEventId: 7, score: 0.62, probability: 0.81 },
      { eventId: 3, currentEventId: 7, score: 0.6, probability: 0.3 },
      { eventId: 5, currentEventId: 5, score: 0.45, probability: null },
    ],
    judgementId: 88,
    judgement: {
      id: 88,
      evaluator: "jev",
      modelVersion: "jev-1.14.0",
      wordingVersion: "membership-1",
      inputTokens: 1812,
      judgedAt: "2026-11-05T00:09:00Z",
      candidates: [
        { eventId: 7, probability: 0.81 },
        { eventId: 3, probability: 0.3 },
      ],
    },
  },
  keyTerms: [
    {
      modelVersion: "e5-small-int8",
      terms: ["국회", "본회의", "예산안"],
      createdAt: "2026-11-05T00:00:00Z",
    },
  ],
};

function renderPage(id: number, response: () => Response) {
  return renderAdminRoutes(
    [{ path: "/admin/articles/:id", element: <ArticleDetailPage /> }],
    `/admin/articles/${id}`,
    { [`GET /api/admin/articles/${id}`]: response },
  ).server;
}

describe("ArticleDetailPage", () => {
  it("기사 정보 · 버전마다 평가 · 평가 상세 링크를 보인다", async () => {
    renderPage(41, () => json(DETAIL));

    const article = await screen.findByRole("region", { name: "기사" });
    expect(
      within(article).getByRole("heading", { name: "국회 본회의 통과" }),
    ).toBeInTheDocument();
    expect(
      within(article).getByRole("link", { name: "원문 열기" }),
    ).toHaveAttribute("href", "https://news.example/a/41");
    expect(article).toHaveTextContent("<b>요약</b> 원문");
    const feeds = within(
      within(article).getByRole("list", { name: "실린 피드" }),
    ).getAllByRole("listitem");
    expect(feeds[0]).toHaveTextContent(/^한겨레 · 정치 · 카테고리 정치$/);
    expect(feeds[1]).toHaveTextContent(/^이름 없는 출처$/);

    const rows = within(
      screen.getByRole("table", { name: "평가" }),
    ).getAllByRole("row");
    const latest = within(rows[1]!).getAllByRole("cell");
    expect(latest[0]).toHaveTextContent(/^2$/);
    expect(latest[2]).toHaveTextContent(/^low_confidence$/);
    expect(latest[3]).toHaveTextContent(/^국회·정당·선거 0\.4$/);
    expect(latest[4]).toHaveTextContent(/^없음$/);
    expect(latest[6]).toHaveTextContent(/^없음$/);
    expect(
      within(latest[7]!).getByRole("link", { name: "평가 19" }),
    ).toHaveAttribute("href", "/admin/evaluations/19");
    const earlier = within(rows[2]!).getAllByRole("cell");
    expect(earlier[4]).toHaveTextContent(/^국내 0\.9$/);
    expect(earlier[6]).toHaveTextContent(/^3\.25$/);
  });

  it("사건 소속 — 배정 · 판정에 쓴 기준 · 저장 후보(합쳐진 후보 포함) · Jev 판정 · 핵심 말을 보인다", async () => {
    renderPage(41, () => json(DETAIL));

    const membership = await screen.findByRole("region", { name: "사건 소속" });
    const assignment = within(membership).getByLabelText("배정");
    expect(
      within(assignment).getByRole("link", { name: "사건 7" }),
    ).toHaveAttribute("href", "/admin/events/7");
    expect(assignment).toHaveTextContent("열림 · 기사 4");
    expect(assignment).toHaveTextContent("Jev");
    expect(assignment).not.toHaveTextContent("이 기사로 새 사건을 열었다");
    expect(assignment).toHaveTextContent(
      "시간 창 3일 · 위 기준 0.8 · 아래 기준 0.55 · 저장 기준 0.4 · 확률 기준 0.5",
    );

    const candidates = within(
      within(membership).getByRole("table", { name: "저장 후보" }),
    ).getAllByRole("row");
    expect(within(candidates[1]!).getAllByRole("cell")[0]).toHaveTextContent(
      /^사건 7$/,
    );
    const merged = within(candidates[2]!).getAllByRole("cell");
    expect(merged[0]).toHaveTextContent(/^사건 3 — 사건 7에 합쳐짐$/);
    expect(within(merged[0]!).getByRole("link")).toHaveAttribute(
      "href",
      "/admin/events/3",
    );
    expect(merged[2]).toHaveTextContent(/^0\.3$/);
    expect(within(candidates[3]!).getAllByRole("cell")[2]).toHaveTextContent(
      /^묻지 않음$/,
    );

    const judgement = within(membership).getByRole("group", {
      name: "Jev 판정",
    });
    expect(judgement).toHaveTextContent(
      "jev jev-1.14.0 · 문구 membership-1 · 입력 토큰 1,812",
    );
    const asked = within(
      within(judgement).getByRole("list", { name: "물은 후보" }),
    ).getAllByRole("listitem");
    expect(asked.map((item) => item.textContent)).toEqual([
      "사건 7 0.81",
      "사건 3 0.3",
    ]);

    const keyTerms = screen.getByRole("region", { name: "핵심 말" });
    expect(keyTerms).toHaveTextContent("e5-small-int8");
    expect(keyTerms).toHaveTextContent("국회 · 본회의 · 예산안");
  });

  it("평가 · 배정 · 표현이 없는 기사는 그렇다고 알린다", async () => {
    renderPage(43, () =>
      json({
        ...DETAIL,
        id: 43,
        article: { ...DETAIL.article, summary: null },
        evaluations: [],
        assignment: null,
        keyTerms: [],
      }),
    );

    const article = await screen.findByRole("region", { name: "기사" });
    expect(article).toHaveTextContent("요약문 없음");
    expect(screen.getByRole("region", { name: "평가" })).toHaveTextContent(
      "평가가 없다",
    );
    expect(screen.queryByRole("table", { name: "평가" })).toBeNull();
    const membership = screen.getByRole("region", { name: "사건 소속" });
    expect(membership).toHaveTextContent("사건에 배정되지 않았다");
    expect(within(membership).queryByRole("link")).toBeNull();
    expect(screen.getByRole("region", { name: "핵심 말" })).toHaveTextContent(
      "기사 표현이 없다",
    );
  });

  it("Jev 판정이 없는 배정 · 지운 판정 원본 · 사라진 사건을 구별해 알린다", async () => {
    const assignment = DETAIL.assignment!;
    renderPage(41, () =>
      json({
        ...DETAIL,
        assignment: {
          ...assignment,
          event: null,
          decidedBy: "FORMULA",
          opened: true,
          candidates: [],
          judgementId: null,
          judgement: null,
        },
      }),
    );

    const membership = await screen.findByRole("region", { name: "사건 소속" });
    const values = within(membership).getByLabelText("배정");
    expect(values).toHaveTextContent("사건 7 — 지금 값을 읽지 못했다");
    expect(values).toHaveTextContent("산식 · 이 기사로 새 사건을 열었다");
    expect(membership).toHaveTextContent("저장 기준 이상인 후보가 없었다");
    expect(
      within(membership).getByRole("group", { name: "Jev 판정" }),
    ).toHaveTextContent("이 배정이 반영한 Jev 판정이 없다");
  });

  it("판정 원본 ID 는 있지만 원본을 지웠으면 그렇다고 알린다", async () => {
    renderPage(41, () =>
      json({
        ...DETAIL,
        assignment: { ...DETAIL.assignment!, judgement: null },
      }),
    );

    expect(
      await screen.findByRole("group", { name: "Jev 판정" }),
    ).toHaveTextContent("판정 원본 88이 없다 — 보존 기간이 지나 지웠다");
  });

  it("없는(지운) 기사는 서버의 설명과 함께 알린다", async () => {
    renderPage(42, () => problem(404, "없는 기사: 42"));

    expect(await screen.findByRole("alert")).toHaveTextContent("없는 기사: 42");
    expect(screen.queryByRole("region", { name: "기사" })).toBeNull();
  });
});
