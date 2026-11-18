import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DigestHomePage } from "@/features/digests/DigestHomePage";
import type {
  DigestHome,
  DigestRequestResult,
} from "@/features/digests/digestQueries";
import { attempt, day, home, status } from "@/features/digests/digestTestKit";
import type {
  InterestSettings,
  InterestTags,
} from "@/features/interests/interestQueries";
import { type Handler, json, problem } from "@/test/fakeServer";
import { renderRoutes } from "@/test/renderRoutes";

function tags(topics: string[]): InterestTags {
  return {
    tagSet: { id: 1, number: 1, topics: [], regions: [], formats: [] },
    preferences: { topics, excludedRegions: [], excludedFormats: [] },
  };
}

function settings(overrides: Partial<InterestSettings> = {}): InterestSettings {
  return {
    timezone: "Asia/Seoul",
    digestTime: "07:00",
    interestText: "",
    interestTextMax: 500,
    weeklyReport: true,
    weeklyReportDay: "MONDAY",
    monthlyReport: true,
    ...overrides,
  };
}

function renderHome(
  responses: DigestHome[] | DigestHome,
  extra: Record<string, Handler> = {},
  topics: string[] = ["politics"],
) {
  const queue = Array.isArray(responses) ? [...responses] : [responses];
  return renderRoutes([{ path: "/", element: <DigestHomePage /> }], "/", {
    "GET /api/digests/latest": () =>
      json(queue.length > 1 ? queue.shift() : queue[0]),
    "GET /api/interests/tags": () => json(tags(topics)),
    "GET /api/interests/settings": () => json(settings()),
    ...extra,
  });
}

function latestRequests(server: ReturnType<typeof renderHome>["server"]) {
  return server.requests.filter((r) => r.path === "/api/digests/latest").length;
}

afterEach(() => {
  vi.useRealTimers();
});

describe("DigestHomePage", () => {
  it("가장 늦은 날짜의 다이제스트를 그 날짜와 함께 그린다", async () => {
    renderHome(home());

    expect(
      await screen.findByRole("heading", { level: 1, name: "오늘 다이제스트" }),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole("heading", {
        level: 2,
        name: "2026. 11. 18. 다이제스트",
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "기사 1" })).toHaveAttribute(
      "href",
      "https://news.example/1",
    );
    expect(
      screen.getByRole("link", { name: "이전 다이제스트" }),
    ).toHaveAttribute("href", "/digests/2026-11-17");
    expect(
      screen.getAllByRole("link", { name: "지난 다이제스트" })[0],
    ).toHaveAttribute("href", "/digests");
  });

  it("다이제스트가 하나도 없으면 그렇게 알린다", async () => {
    renderHome(home({ day: null }));

    expect(
      await screen.findByText("아직 만든 다이제스트가 없습니다."),
    ).toBeInTheDocument();
  });

  it("구독한 주제가 없으면 관심 설정으로 안내한다", async () => {
    renderHome(home(), {}, []);

    expect(
      await screen.findByText(
        /구독한 주제가 없어 다이제스트에 구독 기사가 들어가지 않습니다/,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "관심 설정으로 가기" }),
    ).toHaveAttribute("href", "/interests");
  });

  it("구독한 주제가 있으면 관심 설정으로 안내하지 않는다", async () => {
    const { server } = renderHome(home());

    await screen.findByRole("link", { name: "기사 1" });
    await expect
      .poll(() => server.requests.some((r) => r.path === "/api/interests/tags"))
      .toBe(true);
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)));
    expect(screen.queryByText(/구독한 주제가 없어/)).not.toBeInTheDocument();
  });

  it("오늘 다이제스트가 아직 없고 생성 시각 전이면 그 시각을 알린다", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    // 2026-11-19 06:00 KST — 생성 시각 07:00 전
    vi.setSystemTime(new Date("2026-11-18T21:00:00Z"));
    renderHome(
      home({
        day: day({ date: "2026-11-18" }),
        status: status({
          today: "2026-11-19",
          targetDate: "2026-11-19",
          regenerates: false,
        }),
      }),
    );

    expect(
      await screen.findByText("오늘 다이제스트는 07:00에 만듭니다."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "지금 만들기" })).toBeEnabled();
  });

  it("설정 시간대가 브라우저와 다르면 시간대 이름을 붙인다", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    // 2026-11-18 06:00 PST — 생성 시각 07:00 전
    vi.setSystemTime(new Date("2026-11-18T14:00:00Z"));
    renderHome(
      home({
        day: day({ date: "2026-11-17" }),
        status: status({ today: "2026-11-18", regenerates: false }),
      }),
      {
        "GET /api/interests/settings": () =>
          json(settings({ timezone: "America/Los_Angeles" })),
      },
    );

    expect(
      await screen.findByText(
        "오늘 다이제스트는 07:00 (America/Los_Angeles)에 만듭니다.",
      ),
    ).toBeInTheDocument();
  });

  it("생성 시각이 지났는데 오늘 다이제스트가 없으면 아직 없다고만 알린다", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    // 2026-11-19 09:00 KST — 생성 시각 07:00 뒤
    vi.setSystemTime(new Date("2026-11-19T00:00:00Z"));
    renderHome(
      home({
        day: day({ date: "2026-11-18" }),
        status: status({
          today: "2026-11-19",
          targetDate: "2026-11-19",
          regenerates: false,
        }),
      }),
    );

    expect(
      await screen.findByText("오늘 다이제스트가 아직 없습니다."),
    ).toBeInTheDocument();
  });

  it("관심 설정을 읽지 못하면 시각 없이 알린다", async () => {
    renderHome(
      home({
        day: day({ date: "2026-11-17" }),
        status: status({ today: "2026-11-18", regenerates: false }),
      }),
      { "GET /api/interests/settings": () => problem(500, "실패") },
    );

    expect(
      await screen.findByText("오늘 다이제스트가 아직 없습니다."),
    ).toBeInTheDocument();
  });

  it("오늘 다이제스트가 있으면 오늘 안내를 하지 않는다", async () => {
    renderHome(home());

    await screen.findByRole("link", { name: "기사 1" });
    expect(
      screen.queryByText(/오늘 다이제스트가 아직/),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/에 만듭니다/)).not.toBeInTheDocument();
  });

  it("쓸 모델이 없으면 안내하고 만들기를 누를 수 없다", async () => {
    renderHome(home({ status: status({ usableModel: false }) }));

    expect(
      await screen.findByText(/쓸 모델이 없어 다이제스트를 만들지 못합니다/),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 만들기" })).toBeDisabled();
  });

  it("오늘 횟수를 다 썼으면 언제 다시 차는지 알리고 누를 수 없다", async () => {
    renderHome(home({ status: status({ remainingToday: 0 }) }));

    expect(
      await screen.findByText(
        "오늘은 더 만들 수 없습니다. 2026. 11. 19. 오전 12:00 GMT+9부터 다시 만들 수 있습니다.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 만들기" })).toBeDisabled();
  });

  it("다시 만들기는 확인 창에서 비용 · 남은 횟수 · 지금 다이제스트가 남는다는 것을 알린 뒤 요청한다", async () => {
    const user = userEvent.setup();
    const requested: DigestRequestResult = {
      result: "REQUESTED",
      digestDate: "2026-11-18",
      regenerated: true,
      resetsAt: null,
    };
    const { server } = renderHome(home(), {
      "POST /api/digests": () => json(requested),
    });

    await user.click(
      await screen.findByRole("button", { name: "다시 만들기" }),
    );
    const dialog = await screen.findByRole("alertdialog");
    expect(dialog).toHaveTextContent(
      "2026. 11. 18. 다이제스트를 다시 만들까요?",
    );
    expect(dialog).toHaveTextContent(
      "등록한 LLM 키로 공급자에게 요청합니다. 요청 비용은 내 공급자 계정에 청구됩니다.",
    );
    expect(dialog).toHaveTextContent("오늘 3번 더 만들 수 있습니다.");
    expect(dialog).toHaveTextContent(
      "같은 기간으로 다시 만듭니다. 다 만들 때까지 지금 다이제스트가 보이고, 만들지 못하면 지금 다이제스트가 그대로 남습니다.",
    );
    expect(dialog).not.toHaveTextContent("일정 생성은 하지 않습니다");
    await user.click(
      screen
        .getAllByRole("button", { name: "다시 만들기" })
        .at(-1) as HTMLElement,
    );

    await waitFor(() =>
      expect(
        server.requests.filter(
          (r) => r.method === "POST" && r.path === "/api/digests",
        ),
      ).toHaveLength(1),
    );
    // 요청 뒤 홈을 다시 읽는다
    await waitFor(() => expect(latestRequests(server)).toBe(2));
  });

  it("생성 시각 전에 새로 만들면 그날 일정 생성이 빠진다고 알린다", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-11-18T21:00:00Z"));
    const user = userEvent.setup();
    renderHome(
      home({
        day: day({ date: "2026-11-18" }),
        status: status({
          today: "2026-11-19",
          targetDate: "2026-11-19",
          regenerates: false,
        }),
      }),
    );

    await user.click(
      await screen.findByRole("button", { name: "지금 만들기" }),
    );
    const dialog = await screen.findByRole("alertdialog");
    expect(dialog).toHaveTextContent(
      "2026. 11. 19. 다이제스트를 지금 만들까요?",
    );
    expect(dialog).toHaveTextContent(
      "지금 만들면 오늘 07:00 일정 생성은 하지 않습니다.",
    );
    expect(dialog).not.toHaveTextContent("같은 기간으로 다시 만듭니다");
  });

  it("생성 시각이 지나 새로 만들면 일정 생성 문구를 두지 않는다", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-11-19T00:00:00Z"));
    const user = userEvent.setup();
    renderHome(
      home({
        day: day({ date: "2026-11-18" }),
        status: status({
          today: "2026-11-19",
          targetDate: "2026-11-19",
          regenerates: false,
        }),
      }),
    );

    await user.click(
      await screen.findByRole("button", { name: "지금 만들기" }),
    );
    const dialog = await screen.findByRole("alertdialog");
    expect(dialog).not.toHaveTextContent("일정 생성은 하지 않습니다");
  });

  it.each([
    [
      {
        result: "IN_PROGRESS",
        digestDate: null,
        regenerated: null,
        resetsAt: null,
      },
      "이미 만드는 중인 다이제스트가 있습니다.",
    ],
    [
      {
        result: "DAILY_LIMIT_REACHED",
        digestDate: null,
        regenerated: null,
        resetsAt: "2026-11-18T15:00:00Z",
      },
      "오늘은 더 만들 수 없습니다. 2026. 11. 19. 오전 12:00 GMT+9부터 다시 만들 수 있습니다.",
    ],
    [
      {
        result: "NO_USABLE_MODEL",
        digestDate: null,
        regenerated: null,
        resetsAt: null,
      },
      "쓸 모델이 없어 다이제스트를 만들지 못합니다. LLM 키와 관심 설정의 모델을 확인하세요.",
    ],
  ] as [DigestRequestResult, string][])(
    "받지 않은 요청은 그 까닭을 알린다 — %o",
    async (result, text) => {
      const user = userEvent.setup();
      renderHome(home(), { "POST /api/digests": () => json(result) });

      await user.click(
        await screen.findByRole("button", { name: "다시 만들기" }),
      );
      await screen.findByRole("alertdialog");
      await user.click(
        screen
          .getAllByRole("button", { name: "다시 만들기" })
          .at(-1) as HTMLElement,
      );

      expect(await screen.findByRole("alert")).toHaveTextContent(text);
    },
  );

  it("만드는 중이면 끝날 때까지 다시 읽고, 끝나면 멈춘다", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const running = home({
      day: day({
        shown: null,
        latest: attempt({ status: "RUNNING", completedAt: null }),
        usage: { attempts: 1, inputTokens: 0, outputTokens: 0 },
      }),
      status: status({ inProgress: true }),
    });
    const { server } = renderHome([running, home()]);

    expect(await screen.findByRole("status")).toHaveTextContent(
      "다이제스트를 만드는 중입니다.",
    );
    expect(screen.getByRole("button", { name: "다시 만들기" })).toBeDisabled();
    await act(() => vi.advanceTimersByTimeAsync(5000));
    expect(await screen.findByRole("link", { name: "기사 1" })).toBeVisible();
    expect(latestRequests(server)).toBe(2);

    await act(() => vi.advanceTimersByTimeAsync(15000));
    expect(latestRequests(server)).toBe(2);
  });
});
