import userEvent from "@testing-library/user-event";
import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ReportPage } from "@/features/reports/ReportPage";
import type { ReportDetail } from "@/features/reports/reportQueries";
import {
  ARTICLE,
  detail,
  storyline,
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

/** 갈래 둘이 있는 주간 리포트 — 스토리라인 둘(정리 있음 · 없음), 사건 둘(기사 있음 · 없음), 직전 리포트 11, 숨긴 섹션 1 */
const READY = detail(summary(12), {
  previous: summary(11, { firstDay: "2026-11-09", lastDay: "2026-11-15" }),
  hiddenSections: 1,
  sections: [
    {
      kind: "STORYLINES",
      title: "추적한 흐름이 커졌다",
      body: "흐름 본문이다.",
      citations: [
        {
          kind: "STORYLINE",
          storyline: storyline(31, {
            userStorylines: [{ id: 5, title: "의대 정원 갈등" }],
          }),
          eventId: null,
          article: null,
        },
        { kind: "EVENT", storyline: null, eventId: 902, article: ARTICLE },
        { kind: "EVENT", storyline: null, eventId: 903, article: null },
      ],
      userStorylines: [
        {
          storyline: storyline(31, {
            userStorylines: [{ id: 5, title: "의대 정원 갈등" }],
          }),
          articles: 8,
          previous: 2,
          trend: "ROSE",
          trackedInPeriod: false,
          state: "ACTIVE",
        },
        {
          storyline: storyline(33),
          articles: 5,
          previous: null,
          trend: "NO_COMPARISON",
          trackedInPeriod: true,
          state: "QUIET",
        },
      ],
      interests: null,
    },
    {
      kind: "INTERESTS",
      title: "관심이 옮겨 갔다",
      body: "관심 본문이다.",
      citations: [
        {
          kind: "STORYLINE",
          storyline: storyline(33),
          eventId: null,
          article: null,
        },
      ],
      userStorylines: null,
      interests: {
        topics: [{ name: "금융", start: 0.42, end: null, shift: "LEFT" }],
        userStorylines: [
          {
            storyline: storyline(34, { title: null }),
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
  it("갈래 섹션마다 고정 제목 · 요지 · 본문 · 표 · 인용을 그 순서로 그린다", async () => {
    renderReport(READY);

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "주간 리포트 · 2026. 11. 16. ~ 2026. 11. 22.",
      }),
    ).toBeInTheDocument();
    expect(
      screen
        .getAllByRole("heading", { level: 2 })
        .map((heading) => heading.textContent),
    ).toEqual(["추적한 흐름", "관심의 변화"]);
    // 모델이 쓴 제목은 고정 제목 아래 요지 한 줄, 이어 본문 · 표
    const storylineSection = screen
      .getByRole("heading", { level: 2, name: "추적한 흐름" })
      .closest("section")!;
    const [heading, lead, body, table] = Array.from(storylineSection.children);
    expect(heading).toHaveTextContent("추적한 흐름");
    expect(lead!.tagName).toBe("P");
    expect(lead).toHaveTextContent("추적한 흐름이 커졌다");
    expect(lead).toHaveClass("font-medium");
    expect(body).toHaveTextContent("흐름 본문이다.");
    expect(
      within(table as HTMLElement).getByRole("table"),
    ).toHaveAccessibleName("보도");

    const userStorylines = screen.getByRole("table", {
      name: "보도",
    });
    const userStorylineRows = within(userStorylines)
      .getAllByRole("row")
      .slice(1);
    expect(userStorylineRows.map((row) => row.textContent)).toEqual([
      "의대 정원 갈등늘었음 (이번 기간 8건 · 직전 기간 2건)",
      "흐름 33 대표 기사이번 기간에 추적 시작비교 없음 (이번 기간 5건 · 직전 기간 없음)",
    ]);
    // 수는 글자로 보이지 않는다 — 판정 낱말 칸의 title 과 화면 낭독에만 있다
    const userStorylineTrend = within(userStorylineRows[0]!).getAllByRole(
      "cell",
    )[0]!;
    expect(userStorylineTrend).toHaveAttribute(
      "title",
      "이번 기간 8건 · 직전 기간 2건",
    );
    expect(within(userStorylineTrend).getByText("늘었음")).toHaveClass(
      "text-primary",
    );
    expect(
      within(userStorylineTrend).getByText("(이번 기간 8건 · 직전 기간 2건)", {
        exact: false,
      }),
    ).toHaveClass("sr-only");
    expect(
      within(userStorylineRows[0]!).getByRole("link", {
        name: "의대 정원 갈등",
      }),
    ).toHaveAttribute("href", "/user-storylines/5");
    expect(
      within(userStorylineRows[1]!).getByRole("link", {
        name: "흐름 33 대표 기사",
      }),
    ).toHaveAttribute("href", "/storylines/33");

    const interestTopics = screen.getByRole("table", {
      name: "주제",
    });
    expect(
      within(interestTopics)
        .getAllByRole("row")
        .slice(1)
        .map((row) => row.textContent),
    ).toEqual(["금융0.42신호 없음사라진 관심"]);
    // 관심 값은 관심사 히스토리의 칸처럼 색 네모 — 값은 title · 화면 낭독에만, 변화는 색 낱말
    const [start, end, shift] = within(
      within(interestTopics).getAllByRole("row")[1]!,
    ).getAllByRole("cell");
    expect(start).toHaveAttribute("title", "기간 시작 · 0.42");
    expect(start!.querySelector("[aria-hidden='true']")).toHaveClass(
      "bg-primary",
    );
    expect(start!.querySelector("[aria-hidden='true']")).toHaveStyle({
      opacity: "0.42",
    });
    expect(within(start!).getByText("0.42")).toHaveClass("sr-only");
    expect(end).toHaveAttribute("title", "기간 끝 · 신호 없음");
    expect(end!.querySelector("[aria-hidden='true']")).toHaveClass("bg-muted");
    expect(within(shift!).getByText("사라진 관심")).toHaveClass(
      "text-destructive",
    );
    const interestUserStorylines = screen.getByRole("table", {
      name: "흐름",
    });
    const unnamed = within(interestUserStorylines).getByRole("link", {
      name: "제목을 아직 정하지 못한 흐름",
    });
    expect(unnamed).toHaveAttribute("href", "/storylines/34");

    const [userStorylineCitations, interestCitations] = screen.getAllByRole(
      "list",
      {
        name: "이 섹션이 다룬 흐름 · 사건",
      },
    );
    const lines = within(userStorylineCitations!).getAllByRole("listitem");
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

  it("실패는 코드의 무슨 일과 남은 다시 만들기를 보이고 메시지는 보이지 않는다", async () => {
    renderReport(
      detail(summary(12, { status: "FAILED", failure: "QUOTA_EXHAUSTED" })),
    );

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("리포트를 만들지 못했습니다.");
    expect(alert).toHaveTextContent(
      "공급자 계정의 잔액이나 지출 한도가 소진됐습니다.",
    );
    expect(alert).toHaveTextContent(
      "이 리포트는 3번 더 다시 만들 수 있습니다.",
    );
    expect(
      within(alert).getByRole("button", { name: "다시 만들기" }),
    ).toBeEnabled();
  });

  it("다시 만들기를 누르면 요청하고 다시 읽어 만드는 중을 보인다", async () => {
    let reads = 0;
    const { server } = renderRoutes(ROUTES, "/reports/12", {
      "GET /api/reports/12": () =>
        json(
          reads++ === 0
            ? detail(summary(12, { status: "FAILED", failure: "INTERRUPTED" }))
            : detail(summary(12, { status: "SCHEDULED", failure: null })),
        ),
      "POST /api/reports/12/regeneration": () =>
        json({ result: "REQUESTED", status: null }),
    });
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "다시 만들기" }),
    );

    expect(
      await screen.findByText("리포트를 만드는 중입니다."),
    ).toBeInTheDocument();
    expect(
      server.requests.filter(
        (request) =>
          request.method === "POST" &&
          request.path === "/api/reports/12/regeneration",
      ),
    ).toHaveLength(1);
  });

  it("다시 만들기가 받아지지 않으면 까닭을 알린다", async () => {
    renderRoutes(ROUTES, "/reports/12", {
      "GET /api/reports/12": () =>
        json(detail(summary(12, { status: "FAILED", failure: "KEY_INVALID" }))),
      "POST /api/reports/12/regeneration": () =>
        json({ result: "NO_USABLE_MODEL", status: null }),
    });
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "다시 만들기" }),
    );

    expect(
      await screen.findByText(
        "쓸 수 있는 모델이 없어 다시 만들지 않았습니다. 유효한 LLM 키와 고른 모델을 확인하세요.",
      ),
    ).toBeInTheDocument();
  });

  it("다시 만들기를 받지 않은 까닭은 다시 읽어 만드는 중이 되어도 남는다", async () => {
    // 다른 곳에서 먼저 다시 만들었다 — 이 화면은 아직 실패를 보이고, 서버는 실패가 아니라고 답한다
    let reads = 0;
    renderRoutes(ROUTES, "/reports/12", {
      "GET /api/reports/12": () =>
        json(
          reads++ === 0
            ? detail(summary(12, { status: "FAILED", failure: "INTERRUPTED" }))
            : detail(summary(12, { status: "SCHEDULED", failure: null })),
        ),
      "POST /api/reports/12/regeneration": () =>
        json({ result: "NOT_FAILED", status: "SCHEDULED" }),
    });
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "다시 만들기" }),
    );

    expect(
      await screen.findByText("리포트를 만드는 중입니다."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("실패한 리포트가 아니라 다시 만들지 않았습니다."),
    ).toBeInTheDocument();
  });

  it("월간 리포트의 흐름 표는 생성 때 상태를 보이고 직전 리포트를 비교했다고 하지 않는다", async () => {
    const month = summary(20, {
      kind: "MONTH",
      firstDay: "2026-10-26",
      lastDay: "2026-11-25",
    });
    renderReport(
      detail(month, {
        previous: summary(19, {
          kind: "MONTH",
          firstDay: "2026-09-26",
          lastDay: "2026-10-25",
        }),
        sections: [
          {
            kind: "STORYLINES",
            title: "흐름이 이어졌다",
            body: "월간 본문이다.",
            citations: [],
            userStorylines: [
              {
                storyline: storyline(31),
                articles: 40,
                previous: null,
                trend: "NO_COMPARISON",
                trackedInPeriod: false,
                state: "ACTIVE",
              },
              {
                storyline: storyline(33),
                articles: 3,
                previous: null,
                trend: "NO_COMPARISON",
                trackedInPeriod: false,
                state: "ENDED",
              },
              {
                storyline: storyline(35),
                articles: 1,
                previous: null,
                trend: "NO_COMPARISON",
                trackedInPeriod: false,
                state: null,
              },
            ],
            interests: null,
          },
        ],
      }),
      20,
    );

    const table = await screen.findByRole("table", { name: "상태" });
    expect(
      within(table)
        .getAllByRole("columnheader")
        .map((header) => header.textContent),
    ).toEqual(["흐름", "상태"]);
    const rows = within(table).getAllByRole("row").slice(1);
    // 상태 낱말은 다른 화면과 같은 정본(진행 · 잠잠 · 끝남), 수는 title · 화면 낭독에만, 상태를 남기지 않은 옛 줄은 빈 칸
    expect(rows.map((row) => row.textContent)).toEqual([
      "흐름 31 대표 기사진행 (이번 기간 40건)",
      "흐름 33 대표 기사끝남 (이번 기간 3건)",
      "흐름 35 대표 기사이번 기간 1건",
    ]);
    const [active] = within(rows[0]!).getAllByRole("cell");
    expect(active).toHaveAttribute("title", "이번 기간 40건");
    expect(within(active!).getByText("진행")).toHaveClass("text-primary");
    expect(
      within(within(rows[1]!).getAllByRole("cell")[0]!).getByText("끝남"),
    ).toHaveClass("text-muted-foreground");
    expect(screen.queryByText(/비교 없음|판정/)).toBeNull();
    expect(
      screen.getByRole("link", {
        name: "직전 월간 리포트 · 2026. 09. 26. ~ 2026. 10. 25.",
      }),
    ).toHaveAttribute("href", "/reports/19");
    expect(screen.queryByText(/비교한/)).toBeNull();
  });

  it("직전 리포트가 없는 월간 리포트는 비교를 말하지 않는다", async () => {
    renderReport(
      detail(
        summary(20, {
          kind: "MONTH",
          firstDay: "2026-10-26",
          lastDay: "2026-11-25",
        }),
      ),
      20,
    );

    expect(
      await screen.findByText(
        "직전 월간 리포트가 없습니다 — 첫 월간 리포트이거나 직전 기간과 이어지지 않습니다.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/비교한/)).toBeNull();
  });

  it("시작하지 않고 실패한 리포트는 비교한 직전 리포트 줄이 없다", async () => {
    renderReport(
      detail(summary(12, { status: "FAILED", failure: "NO_USABLE_MODEL" }), {
        usage: null,
      }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "쓸 수 있는 모델이 없어 만들기를 시작하지 못했습니다.",
    );
    expect(screen.queryByText(/비교한 직전 리포트가 없습니다/)).toBeNull();
  });

  it("다시 만들기 횟수를 다 쓴 실패는 버튼 없이 안내한다", async () => {
    renderReport(
      detail(
        summary(12, { status: "FAILED", failure: "PROVIDER_UNAVAILABLE" }),
        {
          regenerationsLeft: 0,
        },
      ),
    );

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(
      "이 리포트는 다시 만들 수 있는 횟수를 다 썼습니다.",
    );
    expect(screen.queryByRole("button")).toBeNull();
  });

  it.each([
    [
      "NO_USABLE_MODEL",
      "쓸 수 있는 모델이 없어 만들기를 시작하지 못했습니다. 유효한 LLM 키와 고른 모델을 확인하세요.",
    ],
    ["INTERRUPTED", "만들기가 정한 시간 안에 끝나지 않아 멈췄습니다."],
  ] as const)(
    "일정의 실패 %s 도 그 코드의 무슨 일을 보인다",
    async (failure, text) => {
      renderReport(detail(summary(12, { status: "FAILED", failure })));

      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent("리포트를 만들지 못했습니다.");
      expect(alert).toHaveTextContent(text);
    },
  );

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
