import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { StorylinePage } from "@/features/storylines/StorylinePage";
import type { StorylineCard } from "@/features/storylines/storylineQueries";
import { card } from "@/features/storylines/storylineTestKit";
import type { StorylineSubscriptions } from "@/features/storylines/subscriptionQueries";
import { SubscriptionsPage } from "@/features/storylines/SubscriptionsPage";
import { type Handler, json } from "@/test/fakeServer";
import { renderRoutes } from "@/test/renderRoutes";

const ROUTES = [
  { path: "/storylines/subscriptions", element: <SubscriptionsPage /> },
  { path: "/storylines/:id", element: <StorylinePage /> },
];

const PAGE = "/storylines/subscriptions";

/** 구독 셋 — 구독 시각 t3 > t2 > t1, 마지막 기사는 B(t4) > A(t1) > C(t0) */
const A = 1;
const B = 2;
const C = 3;

const SUBSCRIBED: StorylineSubscriptions = {
  limit: 100,
  subscriptions: [
    {
      userStorylineId: A,
      subscribedAt: "2026-11-03T00:00:00Z",
      storylineIds: [A],
      state: "ACTIVE",
      title: null,
    },
    {
      userStorylineId: B,
      subscribedAt: "2026-11-02T00:00:00Z",
      storylineIds: [B],
      state: "QUIET",
      title: null,
    },
    {
      userStorylineId: C,
      subscribedAt: "2026-11-01T00:00:00Z",
      storylineIds: [C],
      state: "ENDED",
      title: null,
    },
  ],
};

function titled(id: number, title: string, overrides: Partial<StorylineCard>) {
  const base = card(id, overrides);
  return { ...base, title: base.title && { ...base.title, title } };
}

const CARDS: Record<number, StorylineCard> = {
  [A]: titled(A, "의대 정원", {
    lastArticleAt: "2026-11-01T00:00:00Z",
    state: "ACTIVE",
  }),
  [B]: titled(B, "반도체 수출", {
    lastArticleAt: "2026-11-04T00:00:00Z",
    state: "QUIET",
  }),
  [C]: titled(C, "지방 선거", {
    lastArticleAt: "2026-10-31T00:00:00Z",
    state: "ENDED",
  }),
  9: titled(9, "헌법 소원", {}),
};

/** 카드 처리기의 가짜 — 받은 ids 가운데 CARDS 에 있는 것만 낸다 */
const cardsHandler: Handler = (request) => {
  const ids = new URL(request.url).searchParams
    .getAll("ids")
    .flatMap((value) => value.split(","))
    .map(Number);
  return json({
    cards: ids
      .filter((id) => CARDS[id] !== undefined)
      .map((id) => ({ requestedId: id, storyline: CARDS[id] })),
  });
};

const NO_HISTORY: Record<string, Handler> = {
  "GET /api/storyline-subscriptions/changes": () =>
    json({ changes: [], next: null }),
};

function titlesIn(list: HTMLElement): string[] {
  return within(list)
    .getAllByRole("listitem")
    .map((item) => within(item).getByRole("heading").textContent ?? "");
}

describe("SubscriptionsPage", () => {
  it("이어지는 것과 끝난 것을 나누고 기본은 최근 기사 순이다", async () => {
    renderRoutes(ROUTES, PAGE, {
      "GET /api/storyline-subscriptions": () => json(SUBSCRIBED),
      "GET /api/storyline-cards": cardsHandler,
      ...NO_HISTORY,
    });

    const following = await screen.findByRole("list", {
      name: "이어지는 흐름",
    });
    expect(titlesIn(following)).toEqual(["반도체 수출", "의대 정원"]);
    const ended = screen.getByRole("list", { name: "끝난 흐름" });
    expect(titlesIn(ended)).toEqual(["지방 선거"]);
    expect(screen.getByRole("combobox", { name: "순서" })).toHaveValue(
      "LATEST_ARTICLE",
    );
    expect(screen.getByText("추적 3 / 100")).toBeInTheDocument();
    const first = within(following).getAllByRole("listitem")[0]!;
    expect(
      within(first).getByRole("link", { name: "반도체 수출" }),
    ).toHaveAttribute("href", "/storylines/2");
    expect(first).toHaveTextContent(/^반도체 수출추적 해제잠잠 · 마지막 기사 /);
    // 구독한 흐름의 소식은 구독하지 않은 주제여도 다이제스트에 들 수 있다
    expect(
      screen.getByText(
        /구독하지 않은 주제여도 그 흐름의 소식을 받을 수 있습니다/,
      ),
    ).toBeInTheDocument();
  });

  it("순서를 고르면 구독한 순으로 바뀌고 주소에 남는다", async () => {
    const { router } = renderRoutes(ROUTES, PAGE, {
      "GET /api/storyline-subscriptions": () => json(SUBSCRIBED),
      "GET /api/storyline-cards": cardsHandler,
      ...NO_HISTORY,
    });
    await screen.findByRole("list", { name: "이어지는 흐름" });

    await userEvent.selectOptions(
      screen.getByRole("combobox", { name: "순서" }),
      "SUBSCRIBED",
    );

    expect(
      titlesIn(screen.getByRole("list", { name: "이어지는 흐름" })),
    ).toEqual(["의대 정원", "반도체 수출"]);
    expect(router.state.location.search).toBe("?order=SUBSCRIBED");
  });

  it("주소의 순서로 시작한다", async () => {
    renderRoutes(ROUTES, `${PAGE}?order=SUBSCRIBED`, {
      "GET /api/storyline-subscriptions": () => json(SUBSCRIBED),
      "GET /api/storyline-cards": cardsHandler,
      ...NO_HISTORY,
    });
    expect(
      titlesIn(await screen.findByRole("list", { name: "이어지는 흐름" })),
    ).toEqual(["의대 정원", "반도체 수출"]);
  });

  it("모르는 순서 값이면 기본(최근 기사 순)이다", async () => {
    renderRoutes(ROUTES, `${PAGE}?order=OLDEST`, {
      "GET /api/storyline-subscriptions": () => json(SUBSCRIBED),
      "GET /api/storyline-cards": cardsHandler,
      ...NO_HISTORY,
    });
    expect(
      titlesIn(await screen.findByRole("list", { name: "이어지는 흐름" })),
    ).toEqual(["반도체 수출", "의대 정원"]);
  });

  it("추적 씨앗이 둘인 흐름도 한 줄이고, 정리가 있으면 정리의 제목으로 개인 흐름 화면에 · 없으면 카드 제목으로 타임라인에 잇는다", async () => {
    // 흐름 7 은 A 와 B 를 눌러 추적했고 정리가 있다. C 는 정리 없는 다른 흐름이다
    const { server } = renderRoutes(ROUTES, PAGE, {
      "GET /api/storyline-subscriptions": () =>
        json({
          limit: 100,
          subscriptions: [
            {
              userStorylineId: 7,
              subscribedAt: "2026-11-03T00:00:00Z",
              storylineIds: [A, B],
              state: "ACTIVE",
              title: "의대 정원 갈등의 전개",
            },
            SUBSCRIBED.subscriptions[2]!,
          ],
        }),
      "GET /api/storyline-cards": cardsHandler,
      [`DELETE /api/storyline-subscriptions/${A}`]: () =>
        json({ result: "UNSUBSCRIBED" }),
      ...NO_HISTORY,
    });

    const following = await screen.findByRole("list", {
      name: "이어지는 흐름",
    });
    const rows = within(following).getAllByRole("listitem");
    expect(rows).toHaveLength(1);
    expect(
      within(rows[0]!).getByRole("link", { name: "의대 정원 갈등의 전개" }),
    ).toHaveAttribute("href", "/user-storylines/7");
    expect(
      within(screen.getByRole("list", { name: "끝난 흐름" })).getByRole(
        "link",
        { name: "지방 선거" },
      ),
    ).toHaveAttribute("href", `/storylines/${C}`);
    expect(screen.getByText("추적 2 / 100")).toBeInTheDocument();
    // 카드는 흐름마다 첫 추적 씨앗만 묻는다
    expect(
      server.requests
        .filter((r) => r.path === "/api/storyline-cards")
        .map((r) =>
          new URLSearchParams(r.search)
            .getAll("ids")
            .flatMap((value) => value.split(","))
            .map(Number),
        ),
    ).toEqual([[A, C]]);

    // 해제는 첫 추적 씨앗으로 보낸다
    await userEvent.click(
      within(rows[0]!).getByRole("button", { name: "추적 해제" }),
    );
    await waitFor(() =>
      expect(
        server.requests.filter((r) => r.method === "DELETE").map((r) => r.path),
      ).toEqual([`/api/storyline-subscriptions/${A}`]),
    );
  });

  it("카드 ID 가 100개를 넘으면 100개씩 나눠 묻는다", async () => {
    // 카드 조회의 나눠 묻기를 본다 — 흐름 101개(서버의 상한 밖)를 응답으로 준다
    const ids = Array.from({ length: 101 }, (_, index) => index + 1);
    const { server } = renderRoutes(ROUTES, PAGE, {
      "GET /api/storyline-subscriptions": () =>
        json({
          limit: 100,
          subscriptions: ids.map((storylineId) => ({
            userStorylineId: storylineId,
            subscribedAt: "2026-11-03T00:00:00Z",
            storylineIds: [storylineId],
            state: "ACTIVE",
            title: null,
          })),
        }),
      "GET /api/storyline-cards": cardsHandler,
      ...NO_HISTORY,
    });

    // 둘째 요청까지 받아 줄을 그린다 — 카드가 있는 셋이 제목으로 보인다
    expect(await screen.findByText("추적 101 / 100")).toBeInTheDocument();
    expect(await screen.findByText("의대 정원")).toBeInTheDocument();
    const reads = server.requests
      .filter((r) => r.path === "/api/storyline-cards")
      .map(
        (r) =>
          new URLSearchParams(r.search)
            .getAll("ids")
            .flatMap((value) => value.split(",")).length,
      );
    expect(reads).toEqual([100, 1]);
  });

  it("추적 해제를 누르면 그 ID 로 해제하고 다시 읽어 행이 빠진다", async () => {
    let current = SUBSCRIBED;
    const { server } = renderRoutes(ROUTES, PAGE, {
      "GET /api/storyline-subscriptions": () => json(current),
      "GET /api/storyline-cards": cardsHandler,
      [`DELETE /api/storyline-subscriptions/${A}`]: () => {
        current = {
          ...current,
          subscriptions: current.subscriptions.filter(
            (one) => one.storylineIds[0] !== A,
          ),
        };
        return json({ result: "UNSUBSCRIBED" });
      },
      ...NO_HISTORY,
    });
    const following = await screen.findByRole("list", {
      name: "이어지는 흐름",
    });
    const row = within(following).getAllByRole("listitem")[1]!;

    await userEvent.click(
      within(row).getByRole("button", { name: "추적 해제" }),
    );

    await waitFor(() =>
      expect(
        titlesIn(screen.getByRole("list", { name: "이어지는 흐름" })),
      ).toEqual(["반도체 수출"]),
    );
    expect(
      server.requests.filter((r) => r.method === "DELETE").map((r) => r.path),
    ).toEqual([`/api/storyline-subscriptions/${A}`]);
    expect(screen.getByText("추적 2 / 100")).toBeInTheDocument();
  });

  it("카드를 받지 못한 정리 없는 흐름은 사라진 흐름으로 끝에 둔다", async () => {
    renderRoutes(ROUTES, PAGE, {
      "GET /api/storyline-subscriptions": () =>
        json({
          limit: 100,
          subscriptions: [
            {
              userStorylineId: 4,
              subscribedAt: "2026-11-05T00:00:00Z",
              storylineIds: [4],
              state: "ACTIVE",
              title: null,
            },
            ...SUBSCRIBED.subscriptions,
          ],
        }),
      "GET /api/storyline-cards": cardsHandler,
      ...NO_HISTORY,
    });

    expect(
      titlesIn(await screen.findByRole("list", { name: "이어지는 흐름" })),
    ).toEqual(["반도체 수출", "의대 정원", "사라진 흐름"]);
  });

  it("구독이 없으면 안내하고, 지금 ID 가 없는 기록 쪽이면 카드를 묻지 않는다", async () => {
    const { server } = renderRoutes(ROUTES, PAGE, {
      "GET /api/storyline-subscriptions": () =>
        json({ limit: 100, subscriptions: [] }),
      "GET /api/storyline-subscriptions/changes": () =>
        json({
          changes: [
            {
              storylineId: null,
              action: "ADD",
              occurredAt: "2026-11-02T00:00:00Z",
            },
          ],
          next: null,
        }),
    });

    expect(
      await screen.findByText(
        "추적 중인 흐름이 없습니다. 다이제스트 항목이나 사건 타임라인의 흐름 추적 버튼으로 추적할 수 있습니다.",
      ),
    ).toBeInTheDocument();
    const history = await screen.findByRole("list", { name: "추적 기록" });
    expect(within(history).getByRole("listitem")).toHaveTextContent(
      "흐름 추적 · 사라진 흐름",
    );
    expect(
      server.requests.filter((r) => r.path === "/api/storyline-cards"),
    ).toHaveLength(0);
  });

  it("구독 기록은 최근부터 행마다 구독 · 해제와 지금 스토리라인을 보이고, 더 보기는 받은 커서를 그대로 넘긴다", async () => {
    const { server } = renderRoutes(ROUTES, PAGE, {
      "GET /api/storyline-subscriptions": () =>
        json({ limit: 100, subscriptions: [] }),
      "GET /api/storyline-cards": cardsHandler,
      "GET /api/storyline-subscriptions/changes": (request) =>
        new URL(request.url).searchParams.has("beforeId")
          ? json({
              changes: [
                {
                  storylineId: 9,
                  action: "ADD",
                  occurredAt: "2026-11-01T00:00:00Z",
                },
              ],
              next: null,
            })
          : json({
              changes: [
                {
                  storylineId: 9,
                  action: "REMOVE",
                  occurredAt: "2026-11-03T00:00:00Z",
                },
                {
                  storylineId: null,
                  action: "ADD",
                  occurredAt: "2026-11-02T00:00:00Z",
                },
              ],
              next: { occurredAt: "2026-11-02T00:00:00.123456Z", id: 41 },
            }),
    });
    const history = await screen.findByRole("list", { name: "추적 기록" });
    const rows = within(history).getAllByRole("listitem");
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent("추적 해제 · 헌법 소원");
    expect(
      within(rows[0]!).getByRole("link", { name: "헌법 소원" }),
    ).toHaveAttribute("href", "/storylines/9");
    expect(rows[1]).toHaveTextContent("흐름 추적 · 사라진 흐름");

    await userEvent.click(screen.getByRole("button", { name: "더 보기" }));

    await waitFor(() =>
      expect(
        within(screen.getByRole("list", { name: "추적 기록" })).getAllByRole(
          "listitem",
        ),
      ).toHaveLength(3),
    );
    const pages = server.requests.filter(
      (r) => r.path === "/api/storyline-subscriptions/changes",
    );
    expect(
      pages.map((r) => Object.fromEntries(new URLSearchParams(r.search))),
    ).toEqual([
      {},
      { beforeAt: "2026-11-02T00:00:00.123456Z", beforeId: "41" },
    ]);
    expect(screen.queryByRole("button", { name: "더 보기" })).toBeNull();
  });

  it("구독 기록이 없으면 그렇게 알린다", async () => {
    renderRoutes(ROUTES, PAGE, {
      "GET /api/storyline-subscriptions": () =>
        json({ limit: 100, subscriptions: [] }),
      ...NO_HISTORY,
    });
    expect(
      await screen.findByText("추적 기록이 없습니다."),
    ).toBeInTheDocument();
  });
});
