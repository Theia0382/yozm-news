import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { StorylinesPage } from "@/features/storylines/StorylinesPage";
import {
  subscriptionHandlers,
  trackingStates,
  userCard,
} from "@/features/storylines/storylineTestKit";
import { json, problem } from "@/test/fakeServer";
import { renderRoutes } from "@/test/renderRoutes";

const ROUTES = [
  { path: "/storylines", element: <StorylinesPage /> },
  { path: "/user-storylines/:id", element: <p>개인 흐름 화면</p> },
];

describe("StorylinesPage", () => {
  it("정리된 내 흐름만 받은 순서로 카드마다 제목 링크 · 흐름 요약 · 최근 전개 · 정리 시각을 보인다", async () => {
    const { server } = renderRoutes(ROUTES, "/storylines", {
      "GET /api/user-storylines": () =>
        json({
          userStorylines: [
            userCard(3, { topCount: 2 }),
            userCard(4, {
              title: "반도체 수출 규제",
              summary: "수출 규제가 단계마다 넓어졌다.",
              recent: "새 품목이 더해졌다.",
            }),
          ],
        }),
      "GET /api/storyline-subscriptions/states": (request) =>
        trackingStates(request, () => false),
    });

    expect(
      screen.getByRole("heading", { level: 1, name: "사건 흐름" }),
    ).toBeInTheDocument();
    const list = await screen.findByRole("list", { name: "사건 흐름" });
    const items = within(list).getAllByRole("listitem");
    expect(items).toHaveLength(2);
    // 주소는 사용자 스토리라인 ID — 추적 대상 스토리라인 ID(103)가 아니다
    expect(
      within(items[0]!).getByRole("link", { name: "의대 정원 갈등" }),
    ).toHaveAttribute("href", "/user-storylines/3");
    expect(
      within(items[0]!).getByText("정부와 의료계가 정원을 두고 맞서 왔다."),
    ).toBeInTheDocument();
    expect(items[0]).toHaveTextContent("최근 전개 협의체가 다시 열렸다.");
    expect(items[0]).toHaveTextContent(/정리 .+ · 갈래 2개$/);
    expect(
      within(items[1]!).getByRole("link", { name: "반도체 수출 규제" }),
    ).toHaveAttribute("href", "/user-storylines/4");
    // 갈래가 하나면 갈래 수를 적지 않는다
    expect(items[1]).not.toHaveTextContent("갈래");
    // 공용 스토리라인 목록을 읽지 않는다
    expect(
      server.requests.filter((r) => r.path === "/api/storylines"),
    ).toHaveLength(0);
    expect(
      screen.getByRole("link", { name: "추적 중인 흐름" }),
    ).toHaveAttribute("href", "/storylines/subscriptions");
  });

  it("정리된 흐름이 없으면 어떻게 생기는지 알린다", async () => {
    renderRoutes(ROUTES, "/storylines", {
      "GET /api/user-storylines": () => json({ userStorylines: [] }),
    });

    expect(
      await screen.findByText(/^아직 정리된 흐름이 없습니다\./),
    ).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: "사건 흐름" })).toBeNull();
  });

  it("목록을 읽지 못하면 그렇게 알린다", async () => {
    renderRoutes(ROUTES, "/storylines", {
      "GET /api/user-storylines": () => problem(500, "오류"),
    });

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "흐름을 불러오지 못했습니다.",
    );
  });

  it("카드마다 흐름 추적 버튼이 추적 상태를 따르고, 누르면 그 흐름의 추적 대상 스토리라인으로 추적 · 해제한다", async () => {
    const { server } = renderRoutes(ROUTES, "/storylines", {
      "GET /api/user-storylines": () =>
        json({ userStorylines: [userCard(3), userCard(4)] }),
      ...subscriptionHandlers([103], [103, 104]),
    });
    const list = await screen.findByRole("list", { name: "사건 흐름" });
    const [first, second] = within(list).getAllByRole("listitem");
    const subscribed = await within(first!).findByRole("button", {
      name: "추적 중",
    });
    expect(subscribed).toHaveAttribute("aria-pressed", "true");
    expect(
      within(second!).getByRole("button", { name: "흐름 추적" }),
    ).toHaveAttribute("aria-pressed", "false");
    // 카드 둘의 버튼이 추적 상태 조회 한 번을 함께 쓴다
    const stateReads = () =>
      server.requests.filter(
        (r) => r.path === "/api/storyline-subscriptions/states",
      );
    expect(stateReads().map((r) => r.search)).toEqual(["?ids=103&ids=104"]);

    await userEvent.click(
      within(second!).getByRole("button", { name: "흐름 추적" }),
    );
    expect(
      await within(second!).findByRole("button", { name: "추적 중" }),
    ).toBeInTheDocument();
    await userEvent.click(subscribed);
    expect(
      await within(first!).findByRole("button", { name: "흐름 추적" }),
    ).toBeInTheDocument();

    const changes = server.requests.filter((r) => r.method !== "GET");
    expect(changes.map((r) => `${r.method} ${r.path}`)).toEqual([
      "PUT /api/storyline-subscriptions/104",
      "DELETE /api/storyline-subscriptions/103",
    ]);
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("상한 · 없는 흐름 · 실패는 버튼 옆에 알린다", async () => {
    let answer = (): Response =>
      json({ result: "LIMIT_REACHED", storylineId: null, limit: 100 });
    renderRoutes(ROUTES, "/storylines", {
      "GET /api/user-storylines": () => json({ userStorylines: [userCard(8)] }),
      "GET /api/storyline-subscriptions/states": (request) =>
        trackingStates(request, () => false),
      "PUT /api/storyline-subscriptions/108": () => answer(),
    });
    const button = await screen.findByRole("button", { name: "흐름 추적" });
    await waitFor(() => expect(button).toBeEnabled());

    await userEvent.click(button);
    const limited = await screen.findByRole("alert");
    expect(limited).toHaveTextContent(
      "흐름 추적은 100개까지입니다. 추적 중인 흐름에서 정리해 주세요.",
    );

    answer = () =>
      json({ result: "UNKNOWN_STORYLINE", storylineId: null, limit: null });
    await userEvent.click(button);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "없는 흐름입니다.",
    );

    answer = () => problem(500, "오류");
    await userEvent.click(button);
    await waitFor(() =>
      expect(screen.getByRole("alert")).not.toHaveTextContent(
        "없는 흐름입니다.",
      ),
    );
  });
});
