import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { StorylinePage } from "@/features/storylines/StorylinePage";
import { StorylinesPage } from "@/features/storylines/StorylinesPage";
import {
  card,
  page,
  subscriptionHandlers,
  trackingStates,
} from "@/features/storylines/storylineTestKit";
import { type fakeServer, json, problem } from "@/test/fakeServer";
import { renderRoutes } from "@/test/renderRoutes";

const ROUTES = [
  { path: "/storylines", element: <StorylinesPage /> },
  { path: "/storylines/:id", element: <StorylinePage /> },
];

/** 목록 요청만 — 흐름 추적 버튼이 추적 상태를 함께 읽는다 */
function listRequests(server: ReturnType<typeof fakeServer>) {
  return server.requests.filter((r) => r.path === "/api/storylines");
}

describe("StorylinesPage", () => {
  it("기본 7일 목록을 받아 카드마다 제목 링크 · 큰 표시 · 상태 · 수 · 상위 링크를 보인다", async () => {
    const { server } = renderRoutes(ROUTES, "/storylines", {
      "GET /api/storylines": () =>
        json(
          page([
            card(7, { period: { articles: 4, big: true } }),
            card(8, { title: null, parentId: 7, state: "QUIET" }),
          ]),
        ),
    });

    const list = await screen.findByRole("list", { name: "스토리라인" });
    const items = within(list).getAllByRole("listitem");
    expect(items).toHaveLength(2);
    expect(
      within(items[0]!).getByRole("link", { name: "국회 본회의 통과" }),
    ).toHaveAttribute("href", "/storylines/7");
    expect(within(items[0]!).getByText("큰 스토리라인")).toBeInTheDocument();
    expect(
      within(items[0]!).getByText("사건 3건 · 이 기간 기사 4건"),
    ).toBeInTheDocument();
    // 대표 기사가 없는 카드 · 하위 카드 · 크지 않은 카드
    expect(
      within(items[1]!).getByRole("link", {
        name: "제목을 아직 정하지 못한 스토리라인",
      }),
    ).toHaveAttribute("href", "/storylines/8");
    expect(within(items[1]!).queryByText("큰 스토리라인")).toBeNull();
    expect(
      within(items[1]!).getByRole("link", { name: "상위 스토리라인 보기" }),
    ).toHaveAttribute("href", "/storylines/7");
    expect(within(items[1]!).getByText(/^잠잠 ·/)).toBeInTheDocument();
    expect(listRequests(server).map((r) => r.search)).toEqual(["?window=WEEK"]);
    expect(screen.getByRole("button", { name: "최근 7일" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(
      screen.getByRole("link", { name: "추적 중인 흐름" }),
    ).toHaveAttribute("href", "/storylines/subscriptions");
  });

  it("기간을 고르면 그 기간으로 다시 받는다", async () => {
    const { server } = renderRoutes(ROUTES, "/storylines", {
      "GET /api/storylines": () => json(page([card(7)])),
    });
    await screen.findByRole("list", { name: "스토리라인" });

    await userEvent.click(screen.getByRole("button", { name: "최근 24시간" }));

    await waitFor(() =>
      expect(listRequests(server).map((r) => r.search)).toEqual([
        "?window=WEEK",
        "?window=DAY",
      ]),
    );
  });

  it("더 보기는 첫 응답의 until 과 커서를 함께 넘긴다", async () => {
    const { server } = renderRoutes(ROUTES, "/storylines", {
      "GET /api/storylines": (request) =>
        new URL(request.url).searchParams.has("afterId")
          ? json(page([card(5)]))
          : json(
              page([card(7)], {
                until: "2026-11-05T09:00:00Z",
                next: { lastArticleAt: "2026-11-04T00:00:00Z", id: 7 },
              }),
            ),
    });
    await screen.findByRole("list", { name: "스토리라인" });

    await userEvent.click(screen.getByRole("button", { name: "더 보기" }));

    await waitFor(() =>
      expect(screen.getAllByRole("listitem")).toHaveLength(2),
    );
    expect(listRequests(server)).toHaveLength(2);
    const params = new URLSearchParams(listRequests(server)[1]!.search);
    expect(Object.fromEntries(params)).toEqual({
      window: "WEEK",
      until: "2026-11-05T09:00:00Z",
      afterLastArticleAt: "2026-11-04T00:00:00Z",
      afterId: "7",
    });
    expect(screen.queryByRole("button", { name: "더 보기" })).toBeNull();
  });

  it("기간에 스토리라인이 없으면 그렇게 알린다", async () => {
    renderRoutes(ROUTES, "/storylines?window=MONTH", {
      "GET /api/storylines": () => json(page([], { window: "MONTH" })),
    });

    expect(
      await screen.findByText("이 기간에 기사가 있는 스토리라인이 없습니다."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "최근 30일" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("카드마다 흐름 추적 버튼이 추적 상태를 따르고, 누르면 그 카드의 ID 로 추적 · 해제한다", async () => {
    const { server } = renderRoutes(ROUTES, "/storylines", {
      "GET /api/storylines": () =>
        json(page([card(7), card(8, { parentId: 7 })])),
      ...subscriptionHandlers([7], [7, 8]),
    });
    const list = await screen.findByRole("list", { name: "스토리라인" });
    const [first, child] = within(list).getAllByRole("listitem");
    const subscribed = await within(first!).findByRole("button", {
      name: "추적 중",
    });
    expect(subscribed).toHaveAttribute("aria-pressed", "true");
    expect(
      within(child!).getByRole("button", { name: "흐름 추적" }),
    ).toHaveAttribute("aria-pressed", "false");
    // 카드 둘의 버튼이 추적 상태 조회 한 번을 함께 쓴다 — 추적 중인 흐름 목록은 읽지 않는다
    const listReads = () =>
      server.requests.filter(
        (r) => r.path === "/api/storyline-subscriptions/states",
      );
    expect(listReads().map((r) => r.search)).toEqual(["?ids=7&ids=8"]);
    expect(
      server.requests.filter((r) => r.path === "/api/storyline-subscriptions"),
    ).toHaveLength(0);

    // 하위 행은 자기 ID 로 추적한다
    await userEvent.click(
      within(child!).getByRole("button", { name: "흐름 추적" }),
    );
    expect(
      await within(child!).findByRole("button", { name: "추적 중" }),
    ).toBeInTheDocument();
    await userEvent.click(subscribed);
    expect(
      await within(first!).findByRole("button", { name: "흐름 추적" }),
    ).toBeInTheDocument();

    const changes = server.requests.filter((r) => r.method !== "GET");
    expect(changes.map((r) => `${r.method} ${r.path}`)).toEqual([
      "PUT /api/storyline-subscriptions/8",
      "DELETE /api/storyline-subscriptions/7",
    ]);
    // 추적 · 해제마다 추적 상태를 한 번씩 다시 읽는다
    expect(listReads()).toHaveLength(3);
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("상한 · 없는 스토리라인 · 실패는 버튼 옆에 알린다", async () => {
    let answer = (): Response =>
      json({ result: "LIMIT_REACHED", storylineId: null, limit: 100 });
    renderRoutes(ROUTES, "/storylines", {
      "GET /api/storylines": () => json(page([card(8)])),
      "GET /api/storyline-subscriptions/states": (request) =>
        trackingStates(request, () => false),
      "PUT /api/storyline-subscriptions/8": () => answer(),
    });
    const button = await screen.findByRole("button", { name: "흐름 추적" });
    await waitFor(() => expect(button).toBeEnabled());

    await userEvent.click(button);
    const limited = await screen.findByRole("alert");
    expect(limited).toHaveTextContent(
      "흐름 추적은 100개까지입니다. 추적 중인 흐름에서 정리해 주세요.",
    );
    expect(
      within(limited).getByRole("link", { name: "추적 중인 흐름" }),
    ).toHaveAttribute("href", "/storylines/subscriptions");

    answer = () =>
      json({ result: "UNKNOWN_STORYLINE", storylineId: null, limit: null });
    await userEvent.click(button);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "없는 스토리라인입니다.",
    );

    answer = () => problem(500, "오류");
    await userEvent.click(button);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "흐름 추적을 바꾸지 못했습니다.",
    );
    expect(button).toHaveAttribute("aria-pressed", "false");
  });

  it("추적 상태를 읽지 못하면 흐름 추적 버튼을 누를 수 없고 그렇게 알린다", async () => {
    renderRoutes(ROUTES, "/storylines", {
      "GET /api/storylines": () => json(page([card(8)])),
      "GET /api/storyline-subscriptions/states": () => problem(500, "오류"),
    });

    const button = await screen.findByRole("button", { name: "흐름 추적" });
    expect(button).toBeDisabled();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "흐름 추적 상태를 불러오지 못해 지금은 추적을 바꿀 수 없습니다.",
    );
    expect(button).toBeDisabled();
  });

  it("카드가 100개를 넘으면 추적 상태를 100개씩 나눠 묻는다", async () => {
    const ids = Array.from({ length: 101 }, (_, index) => index + 1);
    const { server } = renderRoutes(ROUTES, "/storylines", {
      "GET /api/storylines": () => json(page(ids.map((id) => card(id)))),
      "GET /api/storyline-subscriptions/states": (request) =>
        trackingStates(request, (id) => id === 101),
    });

    // 둘째 요청의 답까지 합쳐 읽는다
    const buttons = await screen.findAllByRole("button", { name: "추적 중" });
    expect(buttons).toHaveLength(1);
    const reads = server.requests
      .filter((r) => r.path === "/api/storyline-subscriptions/states")
      .map((r) => new URLSearchParams(r.search).getAll("ids").length);
    expect(reads).toEqual([100, 1]);
  });
});
