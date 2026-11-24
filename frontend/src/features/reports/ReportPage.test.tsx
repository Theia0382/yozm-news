import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ReportPage } from "@/features/reports/ReportPage";
import type { ReportDetail } from "@/features/reports/reportQueries";
import {
  ARTICLE,
  detail,
  flow,
  summary,
} from "@/features/reports/reportTestKit";
import { json, problem } from "@/test/fakeServer";
import { renderRoutes } from "@/test/renderRoutes";

const ROUTES = [{ path: "/reports/:id", element: <ReportPage /> }];

function renderReport(body: ReportDetail | (() => Response), id = 12) {
  return renderRoutes(ROUTES, `/reports/${id}`, {
    [`GET /api/reports/${id}`]:
      typeof body === "function" ? body : () => json(body),
  });
}

/** 갈래 셋이 있는 주간 리포트 — 흐름 둘(정리 있음 · 없음), 사건 둘(기사 있음 · 없음), 직전 리포트 11, 숨긴 섹션 1 */
const READY = detail(summary(12), {
  previous: summary(11, { firstDay: "2026-11-09", lastDay: "2026-11-15" }),
  hiddenSections: 1,
  sections: [
    {
      kind: "OVERVIEW",
      title: null,
      body: "이번 주의 개요다.",
      citations: [],
      flows: null,
      topics: null,
      interests: null,
    },
    {
      kind: "STORYLINES",
      title: "추적한 흐름이 커졌다",
      body: "흐름 본문이다.",
      citations: [
        {
          kind: "STORYLINE",
          flow: flow(31, {
            userStorylines: [{ id: 5, title: "의대 정원 갈등" }],
          }),
          eventId: null,
          article: null,
        },
        { kind: "EVENT", flow: null, eventId: 902, article: ARTICLE },
        { kind: "EVENT", flow: null, eventId: 903, article: null },
      ],
      flows: [
        {
          flow: flow(31, {
            userStorylines: [{ id: 5, title: "의대 정원 갈등" }],
          }),
          articles: 8,
          previous: 2,
          trend: "ROSE",
          trackedInPeriod: false,
        },
        {
          flow: flow(33),
          articles: 5,
          previous: null,
          trend: "NO_COMPARISON",
          trackedInPeriod: true,
        },
      ],
      topics: null,
      interests: null,
    },
    {
      kind: "COVERAGE",
      title: "금융 보도가 늘었다",
      body: "보도량 본문이다.",
      citations: [],
      flows: null,
      topics: [{ name: "금융", articles: 40, previous: 12, trend: "ROSE" }],
      interests: null,
    },
    {
      kind: "INTERESTS",
      title: "관심이 옮겨 갔다",
      body: "관심 본문이다.",
      citations: [
        { kind: "STORYLINE", flow: flow(33), eventId: null, article: null },
      ],
      flows: null,
      topics: null,
      interests: {
        topics: [{ name: "금융", start: 0.42, end: null, shift: "LEFT" }],
        flows: [
          {
            flow: flow(34, { title: null }),
            start: 0.2,
            end: 0.6,
            shift: "ENTERED",
          },
        ],
      },
    },
  ],
});

describe("ReportPage", () => {
  it("개요와 갈래 섹션의 제목 · 본문 · 숫자 표 · 인용을 그린다", async () => {
    renderReport(READY);

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "주간 리포트 · 2026. 11. 16. ~ 2026. 11. 22.",
      }),
    ).toBeInTheDocument();
    expect(screen.getByText("이번 주의 개요다.")).toBeInTheDocument();
    expect(
      screen
        .getAllByRole("heading", { level: 2 })
        .map((heading) => heading.textContent),
    ).toEqual([
      "추적한 흐름이 커졌다",
      "금융 보도가 늘었다",
      "관심이 옮겨 갔다",
    ]);

    const flows = screen.getByRole("table", { name: "추적한 흐름의 보도량" });
    const flowRows = within(flows).getAllByRole("row").slice(1);
    expect(flowRows.map((row) => row.textContent)).toEqual([
      "의대 정원 갈등82늘었음",
      "흐름 33 대표 기사이번 기간에 추적 시작5—비교 없음",
    ]);
    expect(
      within(flowRows[0]!).getByRole("link", { name: "의대 정원 갈등" }),
    ).toHaveAttribute("href", "/user-storylines/5");
    expect(
      within(flowRows[1]!).getByRole("link", { name: "흐름 33 대표 기사" }),
    ).toHaveAttribute("href", "/storylines/33");

    const topics = screen.getByRole("table", { name: "구독한 주제의 보도량" });
    expect(
      within(topics)
        .getAllByRole("row")
        .slice(1)
        .map((row) => row.textContent),
    ).toEqual(["금융4012늘었음"]);

    const interestTopics = screen.getByRole("table", {
      name: "관심의 변화 — 주제",
    });
    expect(
      within(interestTopics)
        .getAllByRole("row")
        .slice(1)
        .map((row) => row.textContent),
    ).toEqual(["금융0.42신호 없음사라진 관심"]);
    const interestFlows = screen.getByRole("table", {
      name: "관심의 변화 — 흐름",
    });
    const unnamed = within(interestFlows).getByRole("link", {
      name: "제목을 아직 정하지 못한 흐름",
    });
    expect(unnamed).toHaveAttribute("href", "/storylines/34");

    const [flowCitations, interestCitations] = screen.getAllByRole("list", {
      name: "이 섹션이 다룬 흐름 · 사건",
    });
    const lines = within(flowCitations!).getAllByRole("listitem");
    expect(
      within(lines[0]!).getByRole("link", { name: "의대 정원 갈등" }),
    ).toHaveAttribute("href", "/user-storylines/5");
    const article = within(lines[1]!).getByRole("link", {
      name: "국회 본회의 통과",
    });
    expect(article).toHaveAttribute("href", "https://news.example/41");
    expect(article).toHaveAttribute("target", "_blank");
    expect(lines[1]).toHaveTextContent("한겨레 · 경향신문");
    expect(lines[2]).toHaveTextContent("대표 기사를 찾지 못한 사건");
    expect(within(lines[2]!).queryByRole("link")).toBeNull();
    expect(
      within(interestCitations!).getByRole("link", {
        name: "흐름 33 대표 기사",
      }),
    ).toHaveAttribute("href", "/storylines/33");
  });

  it("숨긴 섹션 수 · 비교한 직전 리포트 · 모델과 토큰을 보인다", async () => {
    renderReport(READY);

    expect(
      await screen.findByText(
        "인용한 흐름 · 사건이 사라져 섹션 1개를 숨겼습니다.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", {
        name: "비교한 직전 주간 리포트 · 2026. 11. 09. ~ 2026. 11. 15.",
      }),
    ).toHaveAttribute("href", "/reports/11");
    expect(
      screen.getByText(/모델 gpt-5.5-mini · 입력 1,200 · 출력 450 토큰/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "변화 리포트 목록" }),
    ).toHaveAttribute("href", "/reports?kind=WEEK");
  });

  it("직전 리포트가 없으면 비교가 빈 까닭을 적는다", async () => {
    renderReport({ ...READY, previous: null });

    expect(
      await screen.findByText(/비교한 직전 리포트가 없습니다/),
    ).toBeInTheDocument();
  });

  it("인용한 것이 모두 사라진 리포트는 글 대신 안내를 보인다", async () => {
    renderReport(detail(summary(12), { hiddenSections: 2, sections: [] }));

    expect(
      await screen.findByText(
        "이 리포트가 인용한 흐름 · 사건이 모두 사라져 글을 보이지 않습니다.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 2 })).toBeNull();
  });

  it.each([
    ["SCHEDULED", "리포트를 만드는 중입니다."],
    ["RUNNING", "리포트를 만드는 중입니다."],
    ["EMPTY", "이 기간에는 리포트에 담을 변화가 없었습니다"],
  ] as const)(
    "%s 는 상태 문장만 보이고 비교 줄이 없다",
    async (status, text) => {
      renderReport(detail(summary(12, { status, completedAt: null })));

      expect(await screen.findByText(new RegExp(text))).toBeInTheDocument();
      expect(screen.queryByText(/비교한 직전/)).toBeNull();
    },
  );

  it("실패는 코드의 무슨 일만 보이고 메시지 · 다시 만들기가 없다", async () => {
    renderReport(
      detail(summary(12, { status: "FAILED", failure: "QUOTA_EXHAUSTED" })),
    );

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("리포트를 만들지 못했습니다.");
    expect(alert).toHaveTextContent(
      "공급자 계정의 잔액이나 지출 한도가 소진됐습니다.",
    );
    expect(screen.queryByRole("button")).toBeNull();
  });

  it("없는 리포트는 찾을 수 없다고 알리고, 숫자가 아닌 주소는 묻지 않는다", async () => {
    renderReport(() => problem(404, "없는 리포트: 12"));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "없는 리포트입니다.",
    );

    const { server } = renderRoutes(ROUTES, "/reports/abc", {});
    expect((await screen.findAllByRole("alert")).at(-1)).toHaveTextContent(
      "없는 리포트입니다.",
    );
    expect(server.requests).toHaveLength(0);
  });
});
