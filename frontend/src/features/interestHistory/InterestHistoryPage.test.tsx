import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { InterestHistoryPage } from "@/features/interestHistory/InterestHistoryPage";
import type {
  InterestHistory,
  InterestPeriod,
  InterestTopic,
} from "@/features/interestHistory/interestHistoryQueries";
import { json } from "@/test/fakeServer";
import { renderRoutes } from "@/test/renderRoutes";

const ROUTES = [
  { path: "/interest-history", element: <InterestHistoryPage /> },
];

const SPANS: InterestHistory["spans"] = [
  {
    unit: "WEEK",
    choices: ["WEEKS_13", "WEEKS_26", "WEEKS_52"],
    defaultSpan: "WEEKS_13",
  },
  {
    unit: "MONTH",
    choices: ["MONTHS_12", "MONTHS_24", "MONTHS_36"],
    defaultSpan: "MONTHS_12",
  },
];

function period(
  firstDay: string,
  lastDay: string,
  overrides: Partial<InterestPeriod> = {},
): InterestPeriod {
  return {
    firstDay,
    lastDay,
    inProgress: false,
    entered: [],
    left: [],
    subscriptionChanges: [],
    enteredUserStorylines: [],
    leftUserStorylines: [],
    ...overrides,
  };
}

function topic(
  key: string,
  name: string,
  levels: (number | null)[],
  prominent: boolean,
): InterestTopic {
  return {
    key,
    name,
    categoryName: key.startsWith("politics") ? "정치" : "경제",
    levels: levels.map((level) => ({ level })),
    prominent,
  };
}

/** 두 주 — 앞 주에 행정이 생기고 증시 구독을 더함, 지금 주에 행정이 사라짐 */
function twoWeeks(span: InterestHistory["span"] = "WEEKS_13"): InterestHistory {
  return {
    span,
    spans: SPANS,
    periods: [
      period("2026-11-09", "2026-11-15", {
        entered: ["politics.executive"],
        subscriptionChanges: [
          {
            at: "2026-11-11T05:00:00Z",
            action: "ADD",
            topics: ["economy.markets"],
          },
        ],
      }),
      period("2026-11-16", "2026-11-22", {
        inProgress: true,
        left: ["politics.executive"],
      }),
    ],
    topics: [
      topic("politics.executive", "대통령실·행정부", [0.4, 0.15], true),
      topic("politics.legislature", "국회·정당·선거", [0, null], false),
      topic("economy.markets", "증권·투자", [null, -0.25], true),
    ],
    userStorylines: [],
  };
}

describe("InterestHistoryPage", () => {
  it("처음에는 기간 없이 받아 서버가 정한 13주를 고르고, 드러난 주제의 칸과 변화를 보인다", async () => {
    const { server } = renderRoutes(ROUTES, "/interest-history", {
      "GET /api/interest-history": () => json(twoWeeks()),
    });

    const table = await screen.findByRole("table", { name: "주제별 관심" });
    expect(server.requests.map((r) => r.search)).toEqual([""]);
    expect(screen.getByRole("button", { name: "주" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByRole("combobox", { name: "기간" })).toHaveValue(
      "WEEKS_13",
    );
    expect(
      within(screen.getByRole("combobox", { name: "기간" }))
        .getAllByRole("option")
        .map((option) => option.textContent),
    ).toEqual(["최근 13주", "최근 26주", "최근 52주"]);
    const headers = within(table).getAllByRole("columnheader");
    expect(headers.map((header) => header.textContent)).toEqual([
      "주제",
      "11/9",
      "진행 중11/16",
    ]);
    const rows = within(table).getAllByRole("rowheader");
    expect(rows.map((row) => row.textContent)).toEqual([
      "정치 · 대통령실·행정부",
      "경제 · 증권·투자",
    ]);
    expect(within(table).getByText("관심 +0.40")).toBeInTheDocument();
    expect(within(table).getByText("관심 -0.25")).toBeInTheDocument();
    expect(within(table).getByText("신호 없음")).toBeInTheDocument();
    const changes = within(
      screen.getByRole("list", { name: "기간마다 바뀐 것" }),
    ).getAllByRole("listitem");
    // 최근 기간이 먼저
    expect(changes[0]).toHaveTextContent("사라진 관심: 대통령실·행정부");
    expect(changes[1]).toHaveTextContent("새로 생긴 관심: 대통령실·행정부");
    expect(changes[1]).toHaveTextContent("구독 더함: 증권·투자");
  });

  it("드러나지 않은 주제는 접혀 있다가 펼친다", async () => {
    renderRoutes(ROUTES, "/interest-history", {
      "GET /api/interest-history": () => json(twoWeeks()),
    });
    await screen.findByRole("table", { name: "주제별 관심" });
    expect(screen.queryByText(/국회·정당·선거/)).toBeNull();

    await userEvent.click(
      screen.getByRole("button", { name: "그 밖의 주제 1개 보기" }),
    );

    expect(screen.getByText(/국회·정당·선거/)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "그 밖의 주제 접기" }),
    ).toHaveAttribute("aria-expanded", "true");
  });

  it("월로 바꾸면 그 단위의 기본 기간을, 드롭다운은 고른 기간을 받는다", async () => {
    const { server } = renderRoutes(ROUTES, "/interest-history", {
      "GET /api/interest-history": (request) => {
        const span = new URL(request.url).searchParams.get("span");
        return json(
          twoWeeks(
            span === null ? "WEEKS_13" : (span as InterestHistory["span"]),
          ),
        );
      },
    });
    await screen.findByRole("table", { name: "주제별 관심" });

    await userEvent.click(screen.getByRole("button", { name: "월" }));
    await waitFor(() =>
      expect(screen.getByRole("combobox", { name: "기간" })).toHaveValue(
        "MONTHS_12",
      ),
    );
    await userEvent.selectOptions(
      screen.getByRole("combobox", { name: "기간" }),
      "MONTHS_36",
    );

    await waitFor(() =>
      expect(server.requests.map((r) => r.search)).toEqual([
        "",
        "?span=MONTHS_12",
        "?span=MONTHS_36",
      ]),
    );
    expect(screen.getByRole("button", { name: "월" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("신호가 없는 사용자에게는 안내를 보인다", async () => {
    renderRoutes(ROUTES, "/interest-history", {
      "GET /api/interest-history": () =>
        json({
          ...twoWeeks(),
          periods: [period("2026-11-16", "2026-11-22", { inProgress: true })],
          topics: [],
        }),
    });

    expect(
      await screen.findByText(/아직 관심의 변화를 보일 신호가 없습니다/),
    ).toBeInTheDocument();
    expect(screen.queryByRole("table")).toBeNull();
  });

  it("관심이 바뀌는 까닭은 화면 설명과 따로 보인다", async () => {
    renderRoutes(ROUTES, "/interest-history", {
      "GET /api/interest-history": () => json(twoWeeks()),
    });

    const how = await screen.findByRole("region", {
      name: "관심은 어떻게 바뀌나요",
    });
    expect(within(how).getAllByRole("listitem")).toHaveLength(6);
    expect(
      within(how).getByText(/좋아요를 누르면 그 항목 주제의 관심이 오르고/),
    ).toBeInTheDocument();
    // 이미 본 흐름의 반응은 주제 대신 흐름에 들고, 스토리라인 구독은 주제에 들지 않는다
    expect(
      within(how).getByText(
        /이미 본 흐름의 소식에 누른 반응은 주제 대신 그 흐름의 관심에 듭니다/,
      ),
    ).toBeInTheDocument();
    expect(within(how).queryByText(/스토리라인/)).not.toBeInTheDocument();
    expect(
      screen.getByText(
        /^주제와 정리된 흐름마다 관심이 기간에 따라 어떻게 달라졌는지/,
      ),
    ).toBeInTheDocument();
  });

  it("정리된 흐름마다 기간별 관심을 제목 링크와 함께 보이고, 관심이 생기거나 사라진 흐름을 기간에 적는다", async () => {
    const base = twoWeeks();
    renderRoutes(ROUTES, "/interest-history", {
      "GET /api/interest-history": () =>
        json({
          ...base,
          periods: [
            { ...base.periods[0]!, enteredUserStorylines: [3] },
            // 99 는 지금 정리된 흐름이 아니다 — 제목을 몰라 적지 않는다
            { ...base.periods[1]!, leftUserStorylines: [3, 99] },
          ],
          userStorylines: [
            {
              id: 3,
              title: "의대 정원 갈등",
              levels: [{ level: 0.5 }, { level: null }],
            },
          ],
        }),
    });

    const table = await screen.findByRole("table", { name: "흐름별 관심" });
    const row = within(table).getByRole("row", { name: /의대 정원 갈등/ });
    expect(
      within(row).getByRole("link", { name: "의대 정원 갈등" }),
    ).toHaveAttribute("href", "/user-storylines/3");
    expect(
      within(row)
        .getAllByRole("cell")
        .map((cell) => cell.textContent),
    ).toEqual(["관심 +0.50", "신호 없음"]);
    // 주제 표는 그대로 따로 있다
    expect(
      screen.getByRole("table", { name: "주제별 관심" }),
    ).toBeInTheDocument();
    const changes = screen.getByRole("list", { name: "기간마다 바뀐 것" });
    expect(
      within(changes).getByText("관심이 생긴 흐름: 의대 정원 갈등"),
    ).toBeInTheDocument();
    expect(
      within(changes).getByText("관심이 사라진 흐름: 의대 정원 갈등"),
    ).toBeInTheDocument();
  });

  it("정리된 흐름이 없으면 흐름 표를 두지 않는다", async () => {
    renderRoutes(ROUTES, "/interest-history", {
      "GET /api/interest-history": () => json(twoWeeks()),
    });

    await screen.findByRole("table", { name: "주제별 관심" });
    expect(screen.queryByRole("table", { name: "흐름별 관심" })).toBeNull();
    expect(screen.queryByText(/관심이 생긴 흐름/)).toBeNull();
  });

  it("주제 신호가 없어도 정리된 흐름이 있으면 흐름 표를 보인다", async () => {
    renderRoutes(ROUTES, "/interest-history", {
      "GET /api/interest-history": () =>
        json({
          ...twoWeeks(),
          periods: [period("2026-11-16", "2026-11-22", { inProgress: true })],
          topics: [],
          userStorylines: [
            { id: 3, title: "의대 정원 갈등", levels: [{ level: 0.4 }] },
          ],
        }),
    });

    expect(
      await screen.findByRole("table", { name: "흐름별 관심" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("table", { name: "주제별 관심" })).toBeNull();
    expect(screen.queryByText(/아직 관심의 변화를 보일 신호가/)).toBeNull();
  });

  it("요청이 실패하면 알린다", async () => {
    renderRoutes(ROUTES, "/interest-history", {
      "GET /api/interest-history": () => json({}, 500),
    });

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "관심사 히스토리를 불러오지 못했습니다",
    );
  });
});
