import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { StorylinePage } from "@/features/storylines/StorylinePage";
import { StorylinesPage } from "@/features/storylines/StorylinesPage";
import {
  card,
  page,
  subscriptionHandlers,
} from "@/features/storylines/storylineTestKit";
import { type fakeServer, json, problem } from "@/test/fakeServer";
import { renderRoutes } from "@/test/renderRoutes";

const ROUTES = [
  { path: "/storylines", element: <StorylinesPage /> },
  { path: "/storylines/:id", element: <StorylinePage /> },
];

/** 목록 요청만 — 구독 버튼이 내 구독을 함께 읽는다 */
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

  it("카드마다 구독 버튼이 내 구독을 따르고, 누르면 그 카드의 ID 로 구독 · 해제한다", async () => {
    const { server } = renderRoutes(ROUTES, "/storylines", {
      "GET /api/storylines": () =>
        json(page([card(7), card(8, { parentId: 7 })])),
      ...subscriptionHandlers([7], [7, 8]),
    });
    const list = await screen.findByRole("list", { name: "스토리라인" });
    const [first, child] = within(list).getAllByRole("listitem");
    const subscribed = await within(first!).findByRole("button", {
      name: "구독 중",
    });
    expect(subscribed).toHaveAttribute("aria-pressed", "true");
    expect(
      within(child!).getByRole("button", { name: "구독" }),
    ).toHaveAttribute("aria-pressed", "false");
    // 카드 둘의 버튼이 내 구독 조회 한 번을 함께 쓴다
    const listReads = () =>
      server.requests.filter((r) => r.path === "/api/storyline-subscriptions");
    expect(listReads()).toHaveLength(1);

    // 하위 행은 자기 ID 로 구독한다
    await userEvent.click(within(child!).getByRole("button", { name: "구독" }));
    expect(
      await within(child!).findByRole("button", { name: "구독 중" }),
    ).toBeInTheDocument();
    await userEvent.click(subscribed);
    expect(
      await within(first!).findByRole("button", { name: "구독" }),
    ).toBeInTheDocument();

    const changes = server.requests.filter((r) => r.method !== "GET");
    expect(changes.map((r) => `${r.method} ${r.path}`)).toEqual([
      "PUT /api/storyline-subscriptions/8",
      "DELETE /api/storyline-subscriptions/7",
    ]);
    // 구독 · 해제마다 내 구독을 한 번씩 다시 읽는다
    expect(listReads()).toHaveLength(3);
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("상한 · 없는 스토리라인 · 실패는 버튼 옆에 알린다", async () => {
    let answer = (): Response =>
      json({ result: "LIMIT_REACHED", storylineId: null, limit: 100 });
    renderRoutes(ROUTES, "/storylines", {
      "GET /api/storylines": () => json(page([card(8)])),
      "GET /api/storyline-subscriptions": () =>
        json({ limit: 100, subscriptions: [] }),
      "PUT /api/storyline-subscriptions/8": () => answer(),
    });
    const button = await screen.findByRole("button", { name: "구독" });
    await waitFor(() => expect(button).toBeEnabled());

    await userEvent.click(button);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "구독은 100개까지입니다. 내 구독에서 정리해 주세요.",
    );

    answer = () =>
      json({ result: "UNKNOWN_STORYLINE", storylineId: null, limit: null });
    await userEvent.click(button);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "없는 스토리라인입니다.",
    );

    answer = () => problem(500, "오류");
    await userEvent.click(button);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "구독을 바꾸지 못했습니다.",
    );
    expect(button).toHaveAttribute("aria-pressed", "false");
  });

  it("내 구독을 읽기 전에는 구독 버튼을 누를 수 없다", async () => {
    renderRoutes(ROUTES, "/storylines", {
      "GET /api/storylines": () => json(page([card(8)])),
      "GET /api/storyline-subscriptions": () => problem(500, "오류"),
    });

    const button = await screen.findByRole("button", { name: "구독" });
    expect(button).toBeDisabled();
  });
});
