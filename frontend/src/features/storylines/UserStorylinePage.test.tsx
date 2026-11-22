import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { UserStorylinePage } from "@/features/storylines/UserStorylinePage";
import {
  subscriptionHandlers,
  trackingStates,
  userDetail,
} from "@/features/storylines/storylineTestKit";
import { json, problem } from "@/test/fakeServer";
import { renderRoutes } from "@/test/renderRoutes";

const ROUTES = [
  { path: "/user-storylines/:id", element: <UserStorylinePage /> },
  { path: "/storylines", element: <p>사건 흐름 탭</p> },
];

const NO_ITEMS = () => json({ items: [], next: null });
const UNTRACKED = (request: Request) => trackingStates(request, () => false);

describe("UserStorylinePage", () => {
  it("흐름 전체의 정리와 정리 시각을 보이고, 갈래가 하나면 타임라인 링크만 둔다", async () => {
    renderRoutes(ROUTES, "/user-storylines/3", {
      "GET /api/user-storylines/3": () => json(userDetail(3)),
      "GET /api/user-storylines/3/items": NO_ITEMS,
      "GET /api/storyline-subscriptions/states": UNTRACKED,
    });

    expect(
      await screen.findByRole("heading", { level: 1, name: "의대 정원 갈등" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 2, name: "흐름 요약" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("정부와 의료계가 정원을 두고 맞서 왔다."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 2, name: "최근 전개" }),
    ).toBeInTheDocument();
    expect(screen.getByText("협의체가 다시 열렸다.")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "사건 타임라인 보기" }),
    ).toHaveAttribute("href", "/storylines/103");
    expect(screen.queryByRole("list", { name: "이 흐름의 갈래" })).toBeNull();
    expect(
      screen.getByRole("link", { name: "사건 흐름 목록" }),
    ).toHaveAttribute("href", "/storylines");
  });

  it("갈래가 둘 이상이면 갈래마다 정리와 타임라인 링크를 보이고, 글이 없는 갈래는 링크만 둔다", async () => {
    renderRoutes(ROUTES, "/user-storylines/3", {
      "GET /api/user-storylines/3": () =>
        json(
          userDetail(3, {
            tops: [
              {
                storylineId: 103,
                state: "ACTIVE",
                title: "정원 확대 발표",
                summary: "정부가 확대 규모를 내놓았다.",
                recent: "대학별 배정이 나왔다.",
              },
              {
                storylineId: 207,
                state: "QUIET",
                title: null,
                summary: null,
                recent: null,
              },
            ],
          }),
        ),
      "GET /api/user-storylines/3/items": NO_ITEMS,
      "GET /api/storyline-subscriptions/states": UNTRACKED,
    });

    const parts = await screen.findByRole("list", { name: "이 흐름의 갈래" });
    const [written, unwritten] = within(parts).getAllByRole("listitem");
    expect(
      within(written!).getByRole("heading", { name: "정원 확대 발표" }),
    ).toBeInTheDocument();
    expect(written).toHaveTextContent("정부가 확대 규모를 내놓았다.");
    expect(written).toHaveTextContent("최근 전개 대학별 배정이 나왔다.");
    expect(
      within(written!).getByRole("link", { name: "사건 타임라인 보기" }),
    ).toHaveAttribute("href", "/storylines/103");
    expect(
      within(unwritten!).getByRole("heading", {
        name: "아직 정리되지 않은 갈래",
      }),
    ).toBeInTheDocument();
    expect(unwritten).not.toHaveTextContent("최근 전개");
    expect(unwritten).toHaveTextContent(/^아직 정리되지 않은 갈래잠잠 · /);
    expect(
      within(unwritten!).getByRole("link", { name: "사건 타임라인 보기" }),
    ).toHaveAttribute("href", "/storylines/207");
  });

  it("지난 다이제스트 항목을 날짜 화면 링크와 함께 보이고, 더 보기는 받은 커서 두 값을 그대로 넘긴다", async () => {
    const { server } = renderRoutes(ROUTES, "/user-storylines/3", {
      "GET /api/user-storylines/3": () => json(userDetail(3)),
      "GET /api/user-storylines/3/items": (request) =>
        new URL(request.url).searchParams.has("beforeDate")
          ? json({
              items: [{ date: "2026-11-02", kind: "HEADLINE", title: null }],
              next: null,
            })
          : json({
              items: [
                {
                  date: "2026-11-04",
                  kind: "TOP_STORY",
                  title: "협의체 재가동",
                },
              ],
              next: { beforeDate: "2026-11-04", beforePosition: 2 },
            }),
      "GET /api/storyline-subscriptions/states": UNTRACKED,
    });

    const list = await screen.findByRole("list", {
      name: "이 흐름의 지난 다이제스트 항목",
    });
    const first = within(list).getByRole("listitem");
    expect(first).toHaveTextContent("주요 뉴스 협의체 재가동");
    expect(within(first).getByRole("link")).toHaveAttribute(
      "href",
      "/digests/2026-11-04",
    );

    await userEvent.click(screen.getByRole("button", { name: "더 보기" }));
    const rows = await within(list).findAllByRole("listitem");
    expect(rows).toHaveLength(2);
    expect(rows[1]).toHaveTextContent("헤드라인 제목 없는 항목");
    expect(within(rows[1]!).getByRole("link")).toHaveAttribute(
      "href",
      "/digests/2026-11-02",
    );
    expect(
      server.requests
        .filter((r) => r.path === "/api/user-storylines/3/items")
        .map((r) => r.search),
    ).toEqual(["", "?beforeDate=2026-11-04&beforePosition=2"]);
    expect(screen.queryByRole("button", { name: "더 보기" })).toBeNull();
  });

  it("묶인 항목이 없으면 그렇게 알린다", async () => {
    renderRoutes(ROUTES, "/user-storylines/3", {
      "GET /api/user-storylines/3": () => json(userDetail(3)),
      "GET /api/user-storylines/3/items": NO_ITEMS,
      "GET /api/storyline-subscriptions/states": UNTRACKED,
    });

    expect(
      await screen.findByText(
        "아직 이 흐름에 묶인 다이제스트 항목이 없습니다.",
      ),
    ).toBeInTheDocument();
  });

  it("맨 아래에 마지막 정리의 모델 · 토큰을 보이고, 여러 번 정리했으면 모두 합친 토큰을 한 줄 더 보인다", async () => {
    renderRoutes(ROUTES, "/user-storylines/3", {
      "GET /api/user-storylines/3": () =>
        json(
          userDetail(3, {
            usage: {
              provider: "anthropic",
              model: "claude-sonnet-5-5",
              inputTokens: 1200,
              outputTokens: 340,
              calls: 4,
              totalInputTokens: 15300,
              totalOutputTokens: 2100,
            },
          }),
        ),
      "GET /api/user-storylines/3/items": NO_ITEMS,
      "GET /api/storyline-subscriptions/states": UNTRACKED,
    });

    expect(
      await screen.findByText(
        /^모델 claude-sonnet-5-5 · 입력 토큰 1,200 · 출력 토큰 340 · 정리 /,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "이 흐름을 4번 정리했고, 고쳐 쓰거나 실패한 것까지 모두 입력 토큰 15,300 · 출력 토큰 2,100을 썼습니다.",
      ),
    ).toBeInTheDocument();
  });

  it("한 번만 정리했으면 합친 토큰 줄을 두지 않는다", async () => {
    renderRoutes(ROUTES, "/user-storylines/3", {
      "GET /api/user-storylines/3": () => json(userDetail(3)),
      "GET /api/user-storylines/3/items": NO_ITEMS,
      "GET /api/storyline-subscriptions/states": UNTRACKED,
    });

    await screen.findByText(/^모델 claude-sonnet-5-5/);
    expect(screen.queryByText(/이 흐름을 .*번 정리했고/)).toBeNull();
  });

  it("흐름 추적 버튼은 그 흐름의 추적 대상 스토리라인으로 추적한다", async () => {
    const { server } = renderRoutes(ROUTES, "/user-storylines/3", {
      "GET /api/user-storylines/3": () => json(userDetail(3)),
      "GET /api/user-storylines/3/items": NO_ITEMS,
      ...subscriptionHandlers([], [103]),
    });

    await userEvent.click(
      await screen.findByRole("button", { name: "흐름 추적" }),
    );
    expect(
      await screen.findByRole("button", { name: "추적 중" }),
    ).toBeInTheDocument();
    expect(
      server.requests
        .filter((r) => r.method !== "GET")
        .map((r) => `${r.method} ${r.path}`),
    ).toEqual(["PUT /api/storyline-subscriptions/103"]);
  });

  it("없는 흐름이면 그렇게 알리고 항목을 읽지 않는다", async () => {
    const { server } = renderRoutes(ROUTES, "/user-storylines/99", {
      "GET /api/user-storylines/99": () => problem(404, "없는 흐름"),
    });

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "없는 흐름입니다.",
    );
    expect(
      server.requests.filter((r) => r.path.endsWith("/items")),
    ).toHaveLength(0);
  });

  it("경로가 ID 꼴이 아니면 요청 없이 없는 흐름으로 알린다", async () => {
    const { server } = renderRoutes(ROUTES, "/user-storylines/abc", {});

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "없는 흐름입니다.",
    );
    expect(server.requests).toHaveLength(0);
  });

  it("읽지 못하면 그렇게 알린다", async () => {
    renderRoutes(ROUTES, "/user-storylines/3", {
      "GET /api/user-storylines/3": () => problem(500, "오류"),
    });

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "흐름을 불러오지 못했습니다.",
    );
  });
});
