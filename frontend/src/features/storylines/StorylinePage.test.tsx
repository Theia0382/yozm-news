import { screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StorylinePage } from "@/features/storylines/StorylinePage";
import { StorylinesPage } from "@/features/storylines/StorylinesPage";
import userEvent from "@testing-library/user-event";
import {
  ARTICLE,
  card,
  event,
  subscriptionHandlers,
  timeline,
} from "@/features/storylines/storylineTestKit";
import { json, problem } from "@/test/fakeServer";
import { renderRoutes } from "@/test/renderRoutes";

const ROUTES = [
  { path: "/storylines", element: <StorylinesPage /> },
  { path: "/storylines/:id", element: <StorylinePage /> },
];

describe("StorylinePage", () => {
  it("머리 · 관계 넷 · 사건(대표 기사 원문 링크 · 갈래 · 받은 소식)을 보인다", async () => {
    renderRoutes(ROUTES, "/storylines/7", {
      "GET /api/storylines/7": () =>
        json(
          timeline({
            parent: { id: 2, title: "선거 정국", state: "ACTIVE" },
            children: [{ id: 8, title: null, state: "QUIET" }],
            preceding: [{ id: 3, title: "법안 발의", state: "ENDED" }],
            following: [{ id: 9, title: "헌법 소원 제기", state: "ACTIVE" }],
            events: [
              event(70, { receivedOn: "2026-11-04" }),
              event(71, { direct: false, representative: null }),
            ],
          }),
        ),
    });

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "국회 본회의 통과",
      }),
    ).toBeInTheDocument();
    const relations = screen.getByRole("navigation", { name: "관계" });
    expect(
      within(relations).getByRole("link", { name: "선거 정국" }),
    ).toHaveAttribute("href", "/storylines/2");
    expect(
      within(relations).getByRole("link", {
        name: "제목을 아직 정하지 못한 흐름",
      }),
    ).toHaveAttribute("href", "/storylines/8");
    expect(
      within(relations).getByRole("link", { name: "법안 발의" }),
    ).toHaveAttribute("href", "/storylines/3");
    expect(
      within(relations).getByRole("link", { name: "헌법 소원 제기" }),
    ).toHaveAttribute("href", "/storylines/9");
    // 관계 넷은 상위 · 하위 · 앞 흐름 · 이어진 흐름 차례다
    expect(
      within(relations)
        .getAllByRole("heading", { level: 2 })
        .map((heading) => heading.textContent),
    ).toEqual(["상위 흐름", "하위 흐름", "앞 흐름", "이어진 흐름"]);
    expect(screen.getByText(/사건 3건 · 기사 12건$/)).toBeInTheDocument();
    // 머리 · 사건의 원문 링크는 모두 새 창으로, 여는 쪽을 넘기지 않는다
    const originals = screen.getAllByRole("link", { name: "원문" });
    expect(originals.length).toBeGreaterThanOrEqual(2);
    for (const link of originals) {
      expect(link).toHaveAttribute("target", "_blank");
      expect(link).toHaveAttribute("rel", "noopener noreferrer");
    }

    const events = within(
      screen.getByRole("list", { name: "사건" }),
    ).getAllByRole("listitem");
    expect(events).toHaveLength(2);
    const original = within(events[0]!).getByRole("link", {
      name: ARTICLE.title,
    });
    expect(original).toHaveAttribute("href", ARTICLE.url);
    expect(original).toHaveAttribute("target", "_blank");
    expect(original).toHaveAttribute("rel", "noopener noreferrer");
    expect(
      within(events[0]!).getByText(/한겨레 · 경향신문 ·/),
    ).toBeInTheDocument();
    expect(
      within(events[0]!).getByText("받은 소식 · 2026. 11. 04."),
    ).toBeInTheDocument();
    expect(within(events[0]!).queryByText(/갈래/)).toBeNull();
    expect(within(events[1]!).getByText(/· 갈래$/)).toBeInTheDocument();
    expect(
      within(events[1]!).getByText("대표 기사를 아직 읽지 못한 사건"),
    ).toBeInTheDocument();
    expect(within(events[1]!).queryByText(/받은 소식/)).toBeNull();
  });

  it("대표 기사가 없으면 머리에 제목 없음 문구를 보이고 원문 줄을 두지 않는다", async () => {
    renderRoutes(ROUTES, "/storylines/7", {
      "GET /api/storylines/7": () =>
        json(
          timeline({
            storyline: card(7, { title: null }),
            events: [event(70, { representative: null })],
          }),
        ),
    });

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "제목을 아직 정하지 못한 흐름",
      }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "원문" })).toBeNull();
  });

  it("관계가 없으면 관계 구역을 두지 않는다", async () => {
    renderRoutes(ROUTES, "/storylines/7", {
      "GET /api/storylines/7": () => json(timeline()),
    });

    await screen.findByRole("heading", { level: 1 });
    expect(screen.queryByRole("navigation", { name: "관계" })).toBeNull();
  });

  it("합쳐진 스토리라인의 ID 로 오면 남는 스토리라인의 주소로 바꾼다", async () => {
    const { router } = renderRoutes(ROUTES, "/storylines/5", {
      "GET /api/storylines/5": () => json(timeline({ storyline: card(7) })),
      "GET /api/storylines/7": () => json(timeline({ storyline: card(7) })),
    });

    await waitFor(() =>
      expect(router.state.location.pathname).toBe("/storylines/7"),
    );
  });

  it("없는 스토리라인이면 그렇게 알린다", async () => {
    const { server } = renderRoutes(ROUTES, "/storylines/99", {
      "GET /api/storylines/99": () => problem(404, "없는 스토리라인: 99"),
    });
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "없는 흐름입니다.",
    );
    expect(server.requests).toHaveLength(1);
  });

  it("경로가 ID 꼴이 아니면 요청 없이 없는 스토리라인으로 알린다", async () => {
    const { server } = renderRoutes(ROUTES, "/storylines/abc", {});
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "없는 흐름입니다.",
    );
    expect(server.requests).toHaveLength(0);
  });

  it("읽지 못하면 다시 볼 수 있게 알린다", async () => {
    renderRoutes(ROUTES, "/storylines/7", {
      "GET /api/storylines/7": () => problem(500, "오류"),
    });
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "흐름을 불러오지 못했습니다.",
    );
  });

  it("합쳐진 ID 로 와도 흐름 추적 버튼은 남는 스토리라인으로 추적한다", async () => {
    const { server, router } = renderRoutes(ROUTES, "/storylines/5", {
      "GET /api/storylines/5": () => json(timeline({ storyline: card(9) })),
      "GET /api/storylines/9": () => json(timeline({ storyline: card(9) })),
      ...subscriptionHandlers([], [5, 9]),
    });
    // 주소 바꾸기는 늦게 그려질 수 있어, 그 사이 바꾸기 전 화면(5)도 추적 상태를 받아 켜진 버튼을 보인다 — 그 버튼은 바꾸기가
    // 그려지면 사라진다. 남는 스토리라인의 타임라인을 읽기 시작한 뒤(바꾸기가 그려진 뒤) 그린 버튼만 누른다
    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/storylines/9");
      expect(server.requests.map((r) => `${r.method} ${r.path}`)).toContain(
        "GET /api/storylines/9",
      );
    });
    const track = await screen.findByRole("button", { name: "흐름 추적" });
    await waitFor(() => expect(track).toBeEnabled());

    await userEvent.click(track);

    expect(
      await screen.findByRole("button", { name: "추적 중" }),
    ).toHaveAttribute("aria-pressed", "true");
    expect(
      server.requests.filter((r) => r.method === "PUT").map((r) => r.path),
    ).toEqual(["/api/storyline-subscriptions/9"]);
  });
});
