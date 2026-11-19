import { focusManager } from "@tanstack/react-query";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { DigestDayPage } from "@/features/digests/DigestDayPage";
import type { DigestDay } from "@/features/digests/digestQueries";
import {
  attempt,
  citation,
  day,
  type FeedbackStatesBody,
  shown,
} from "@/features/digests/digestTestKit";
import { nextFeedback } from "@/features/digests/feedbackQueries";
import { type Handler, json, problem } from "@/test/fakeServer";
import { bodiesOf, renderRoutes } from "@/test/renderRoutes";

const DATE = "2026-11-18";

afterEach(() => {
  // 창 초점을 흉내 낸 시험이 바꾼 초점 상태를 문서의 상태로 되돌린다
  focusManager.setFocused(undefined);
});

/** 날짜 화면 — 다이제스트 응답은 차례로(마지막 것을 되풀이), 상태 응답도 차례로 */
function renderDay({
  days = [day()],
  states = [{ states: [] }],
  date = DATE,
  extra = {},
}: {
  days?: DigestDay[];
  states?: FeedbackStatesBody[];
  date?: string;
  extra?: Record<string, Handler>;
} = {}) {
  const dayQueue = [...days];
  const stateQueue = [...states];
  return renderRoutes(
    [
      { path: "/digests/:date", element: <DigestDayPage /> },
      { path: "/elsewhere", element: <p>다른 화면</p> },
    ],
    `/digests/${date}`,
    {
      [`GET /api/digests/${date}`]: () =>
        json(dayQueue.length > 1 ? dayQueue.shift() : dayQueue[0]),
      "GET /api/feedback": () =>
        json(stateQueue.length > 1 ? stateQueue.shift() : stateQueue[0]),
      ...extra,
    },
  );
}

function recorded(value: "LIKE" | "DISLIKE" | "NONE"): Response {
  return json({ result: "RECORDED", value });
}

/** 목록의 n 번째 — 없으면 시험이 실패한다 */
function nth(elements: HTMLElement[], index: number): HTMLElement {
  const element = elements[index];
  if (element === undefined) {
    throw new Error(`${index} 번째 요소가 없다 — ${elements.length} 개`);
  }
  return element;
}

/** 기사 하나의 버튼 묶음 — 같은 기사가 여러 자리에 있으면 첫 자리 */
async function group(articleId: number) {
  const places = await screen.findAllByRole("group", {
    name: `기사 ${articleId} 반응`,
  });
  return within(nth(places, 0));
}

function pressed(scope: ReturnType<typeof within>, name: string) {
  return scope.getByRole("button", { name }).getAttribute("aria-pressed");
}

function statesQueries(server: ReturnType<typeof renderDay>["server"]) {
  return server.requests.filter(
    (r) => r.method === "GET" && r.path === "/api/feedback",
  );
}

/** 응답을 늦추는 처리기 — release 를 부르면 그 응답을 낸다 */
function deferred(response: () => Response) {
  let release = () => {};
  const handler: Handler = () =>
    new Promise<Response>((resolve) => {
      release = () => resolve(response());
    });
  return { handler, release: () => release() };
}

describe("nextFeedback", () => {
  it("눌린 버튼을 다시 누르면 되돌리고, 아니면 누른 값이다", () => {
    expect(nextFeedback(null, "LIKE")).toBe("LIKE");
    expect(nextFeedback(null, "DISLIKE")).toBe("DISLIKE");
    expect(nextFeedback("LIKE", "LIKE")).toBe("NONE");
    expect(nextFeedback("LIKE", "DISLIKE")).toBe("DISLIKE");
    expect(nextFeedback("DISLIKE", "DISLIKE")).toBe("NONE");
    expect(nextFeedback("DISLIKE", "LIKE")).toBe("LIKE");
  });
});

describe("다이제스트 화면의 좋아요 · 싫어요", () => {
  it("주요 뉴스 · 헤드라인 항목의 출처 줄마다 버튼 묶음이 있고 개요에는 없다", async () => {
    renderDay();

    for (const id of [1, 2, 3, 4]) {
      const buttons = await group(id);
      await waitFor(() =>
        expect(buttons.getByRole("button", { name: "좋아요" })).toBeEnabled(),
      );
      expect(buttons.getByRole("button", { name: "싫어요" })).toBeEnabled();
    }
    expect(screen.getAllByRole("group")).toHaveLength(4);
    const overview = screen.getByRole("region", { name: "요약" });
    expect(within(overview).queryByRole("button")).toBeNull();
  });

  it("인용 기사의 현재 상태를 한 번에 묻고 눌림으로 그린다", async () => {
    const { server } = renderDay({
      states: [
        {
          states: [
            { articleId: 2, value: "LIKE" },
            { articleId: 4, value: "DISLIKE" },
          ],
        },
      ],
    });

    await waitFor(async () =>
      expect(pressed(await group(2), "좋아요")).toBe("true"),
    );
    expect(pressed(await group(2), "싫어요")).toBe("false");
    expect(pressed(await group(4), "싫어요")).toBe("true");
    expect(pressed(await group(4), "좋아요")).toBe("false");
    expect(pressed(await group(1), "좋아요")).toBe("false");
    expect(pressed(await group(1), "싫어요")).toBe("false");
    expect(statesQueries(server).map((r) => r.search)).toEqual([
      "?articleIds=1&articleIds=2&articleIds=3&articleIds=4",
    ]);
  });

  it("좋아요를 누르면 보이는 다이제스트 ID 와 함께 보내고 응답 뒤 눌린다", async () => {
    const { server } = renderDay({
      extra: { "PUT /api/feedback/1": () => recorded("LIKE") },
    });
    const user = userEvent.setup();
    const buttons = await group(1);
    await waitFor(() =>
      expect(buttons.getByRole("button", { name: "좋아요" })).toBeEnabled(),
    );

    await user.click(buttons.getByRole("button", { name: "좋아요" }));

    await waitFor(() => expect(pressed(buttons, "좋아요")).toBe("true"));
    expect(pressed(buttons, "싫어요")).toBe("false");
    expect(bodiesOf(server, "PUT", "/api/feedback/1")).toEqual([
      { digestId: 11, value: "LIKE" },
    ]);
  });

  it("눌린 좋아요를 다시 누르면 없음으로 되돌린다", async () => {
    const { server } = renderDay({
      states: [{ states: [{ articleId: 2, value: "LIKE" }] }],
      extra: { "PUT /api/feedback/2": () => recorded("NONE") },
    });
    const user = userEvent.setup();
    const buttons = await group(2);
    await waitFor(() => expect(pressed(buttons, "좋아요")).toBe("true"));
    expect(buttons.getByRole("button", { name: "좋아요" })).toHaveAttribute(
      "title",
      "누르면 되돌립니다",
    );

    await user.click(buttons.getByRole("button", { name: "좋아요" }));

    await waitFor(() => expect(pressed(buttons, "좋아요")).toBe("false"));
    expect(pressed(buttons, "싫어요")).toBe("false");
    expect(bodiesOf(server, "PUT", "/api/feedback/2")).toEqual([
      { digestId: 11, value: "NONE" },
    ]);
  });

  it("좋아요인 기사의 싫어요를 누르면 바로 싫어요로 바뀐다", async () => {
    const { server } = renderDay({
      states: [{ states: [{ articleId: 2, value: "LIKE" }] }],
      extra: { "PUT /api/feedback/2": () => recorded("DISLIKE") },
    });
    const user = userEvent.setup();
    const buttons = await group(2);
    await waitFor(() => expect(pressed(buttons, "좋아요")).toBe("true"));

    await user.click(buttons.getByRole("button", { name: "싫어요" }));

    await waitFor(() => expect(pressed(buttons, "싫어요")).toBe("true"));
    expect(pressed(buttons, "좋아요")).toBe("false");
    expect(bodiesOf(server, "PUT", "/api/feedback/2")).toEqual([
      { digestId: 11, value: "DISLIKE" },
    ]);
  });

  it("같은 기사가 두 자리에 있으면 한 번 묻고, 응답 전에는 두 자리 모두 잠기고, 응답 뒤 함께 바뀐다", async () => {
    const twice = day({
      shown: shown({
        sections: [
          {
            kind: "TOP_STORY",
            title: "첫 주요 뉴스",
            body: "요약 하나",
            reason: "SUBSCRIBED",
            citations: [citation(1), citation(2)],
          },
          {
            kind: "TOP_STORY",
            title: "둘째 주요 뉴스",
            body: "요약 둘",
            reason: "SUBSCRIBED",
            citations: [citation(1)],
          },
        ],
      }),
    });
    const put = deferred(() => recorded("LIKE"));
    const { server } = renderDay({
      days: [twice],
      extra: { "PUT /api/feedback/1": put.handler },
    });
    const user = userEvent.setup();
    await screen.findAllByRole("group", { name: "기사 1 반응" });
    await waitFor(() =>
      expect(
        within(
          nth(screen.getAllByRole("group", { name: "기사 1 반응" }), 0),
        ).getByRole("button", { name: "좋아요" }),
      ).toBeEnabled(),
    );
    const places = screen.getAllByRole("group", { name: "기사 1 반응" });
    expect(places).toHaveLength(2);
    expect(statesQueries(server).map((r) => r.search)).toEqual([
      "?articleIds=1&articleIds=2",
    ]);

    await user.click(
      within(nth(places, 1)).getByRole("button", { name: "좋아요" }),
    );

    for (const place of places) {
      await waitFor(() =>
        expect(
          within(place).getByRole("button", { name: "좋아요" }),
        ).toBeDisabled(),
      );
      expect(
        within(place).getByRole("button", { name: "싫어요" }),
      ).toBeDisabled();
    }
    put.release();
    for (const place of places) {
      await waitFor(() =>
        expect(pressed(within(place), "좋아요")).toBe("true"),
      );
      expect(
        within(place).getByRole("button", { name: "좋아요" }),
      ).toBeEnabled();
    }
  });

  it("응답을 기다리는 동안 그 기사의 버튼만 잠긴다", async () => {
    const put = deferred(() => recorded("LIKE"));
    renderDay({ extra: { "PUT /api/feedback/1": put.handler } });
    const user = userEvent.setup();
    const one = await group(1);
    const three = await group(3);
    await waitFor(() =>
      expect(one.getByRole("button", { name: "좋아요" })).toBeEnabled(),
    );

    await user.click(one.getByRole("button", { name: "좋아요" }));

    await waitFor(() =>
      expect(one.getByRole("button", { name: "좋아요" })).toBeDisabled(),
    );
    expect(one.getByRole("button", { name: "싫어요" })).toBeDisabled();
    expect(three.getByRole("button", { name: "좋아요" })).toBeEnabled();
    expect(three.getByRole("button", { name: "싫어요" })).toBeEnabled();
    // 응답 전에는 눌림으로 그리지 않는다 — 서버 응답 뒤에 반영한다
    expect(pressed(one, "좋아요")).toBe("false");
    put.release();
    await waitFor(() => expect(pressed(one, "좋아요")).toBe("true"));
  });

  it("받을 수 없는 기사면 그 줄에 알리고 다이제스트를 다시 읽는다 — 상태는 그대로다", async () => {
    const { server } = renderDay({
      extra: {
        "PUT /api/feedback/1": () =>
          json({ result: "NOT_IN_DIGEST", value: null }),
      },
    });
    const user = userEvent.setup();
    const buttons = await group(1);
    await waitFor(() =>
      expect(buttons.getByRole("button", { name: "좋아요" })).toBeEnabled(),
    );
    const daysBefore = server.requests.filter(
      (r) => r.path === `/api/digests/${DATE}`,
    ).length;

    await user.click(buttons.getByRole("button", { name: "좋아요" }));

    const line = screen
      .getByRole("link", { name: "기사 1" })
      .closest("li") as HTMLElement;
    expect((await within(line).findByRole("alert")).textContent).toBe(
      "이 기사에는 더 반응을 남길 수 없습니다.",
    );
    await waitFor(() =>
      expect(
        server.requests.filter((r) => r.path === `/api/digests/${DATE}`),
      ).toHaveLength(daysBefore + 1),
    );
    expect(pressed(buttons, "좋아요")).toBe("false");
  });

  it("다시 읽은 다이제스트가 그 기사를 빼면 그 줄이 사라지고 남은 기사만 묻는다", async () => {
    const without1 = day({
      shown: shown({
        sections: [
          {
            kind: "TOP_STORY",
            title: "주요 뉴스 제목",
            body: "주요 뉴스 요약",
            reason: "SUBSCRIBED",
            citations: [citation(2), citation(3)],
          },
        ],
      }),
    });
    const { server } = renderDay({
      days: [day(), without1],
      extra: {
        "PUT /api/feedback/1": () =>
          json({ result: "NOT_IN_DIGEST", value: null }),
      },
    });
    const user = userEvent.setup();
    const buttons = await group(1);
    await waitFor(() =>
      expect(buttons.getByRole("button", { name: "좋아요" })).toBeEnabled(),
    );

    await user.click(buttons.getByRole("button", { name: "좋아요" }));

    await waitFor(() =>
      expect(screen.queryByRole("link", { name: "기사 1" })).toBeNull(),
    );
    expect(screen.queryByRole("group", { name: "기사 1 반응" })).toBeNull();
    await waitFor(() =>
      expect(statesQueries(server).map((r) => r.search)).toContain(
        "?articleIds=2&articleIds=3",
      ),
    );
  });

  it("요청 제한에 걸리면 이 계정의 요청 제한 안내를 남은 시간과 함께 보인다", async () => {
    renderDay({
      extra: {
        "PUT /api/feedback/1": () =>
          new Response(
            JSON.stringify({ type: "urn:yozmnews:problem:rate-limited" }),
            {
              status: 429,
              headers: {
                "Content-Type": "application/problem+json",
                "Retry-After": "120",
              },
            },
          ),
      },
    });
    const user = userEvent.setup();
    const buttons = await group(1);
    await waitFor(() =>
      expect(buttons.getByRole("button", { name: "좋아요" })).toBeEnabled(),
    );

    await user.click(buttons.getByRole("button", { name: "좋아요" }));

    const line = screen
      .getByRole("link", { name: "기사 1" })
      .closest("li") as HTMLElement;
    expect((await within(line).findByRole("alert")).textContent).toBe(
      "이 계정의 요청이 너무 많아 잠시 막혔습니다. 2분 뒤에 다시 시도해 주세요.",
    );
    expect(pressed(buttons, "좋아요")).toBe("false");
  });

  it("그 밖의 실패면 남기지 못했다고 알리고 상태는 그대로다", async () => {
    renderDay({
      states: [{ states: [{ articleId: 1, value: "DISLIKE" }] }],
      extra: { "PUT /api/feedback/1": () => problem(500, "서버 오류") },
    });
    const user = userEvent.setup();
    const buttons = await group(1);
    await waitFor(() => expect(pressed(buttons, "싫어요")).toBe("true"));

    await user.click(buttons.getByRole("button", { name: "좋아요" }));

    const line = screen
      .getByRole("link", { name: "기사 1" })
      .closest("li") as HTMLElement;
    expect((await within(line).findByRole("alert")).textContent).toBe(
      "반응을 남기지 못했습니다. 잠시 뒤 다시 눌러 주세요.",
    );
    expect(pressed(buttons, "싫어요")).toBe("true");
    expect(pressed(buttons, "좋아요")).toBe("false");
  });

  it("상태를 읽기 전에는 버튼을 누를 수 없다", async () => {
    const states = deferred(() => json({ states: [] }));
    renderDay({ extra: { "GET /api/feedback": states.handler } });

    const buttons = await group(1);
    expect(buttons.getByRole("button", { name: "좋아요" })).toBeDisabled();
    expect(buttons.getByRole("button", { name: "싫어요" })).toBeDisabled();
    states.release();
    await waitFor(() =>
      expect(buttons.getByRole("button", { name: "좋아요" })).toBeEnabled(),
    );
  });

  it("상태를 읽지 못하면 알리고 모든 버튼을 누를 수 없다", async () => {
    renderDay({
      extra: { "GET /api/feedback": () => problem(500, "서버 오류") },
    });

    expect((await screen.findByRole("alert")).textContent).toBe(
      "좋아요 · 싫어요 상태를 읽지 못해 지금은 누를 수 없습니다.",
    );
    const buttons = screen.getAllByRole("button", { name: /좋아요|싫어요/ });
    expect(buttons).toHaveLength(8);
    for (const button of buttons) {
      expect(button).toBeDisabled();
    }
  });

  it("인용이 없으면 상태를 묻지 않고 버튼 · 효과 안내도 없다", async () => {
    const { server } = renderDay({
      days: [day({ shown: shown({ sections: [] }) })],
    });

    expect(await screen.findByRole("note")).toBeInTheDocument();
    expect(statesQueries(server)).toHaveLength(0);
    expect(screen.queryByRole("group")).toBeNull();
    expect(screen.queryByText(/좋아요 · 싫어요는/)).toBeNull();
  });

  it("인용이 있으면 효과 안내 한 줄이 선정 이유 표시와 무관하게 보인다", async () => {
    const subscribedOnly = day({
      shown: shown({
        sections: [
          {
            kind: "TOP_STORY",
            title: "주요 뉴스 제목",
            body: "주요 뉴스 요약",
            reason: "SUBSCRIBED",
            citations: [citation(1)],
          },
        ],
      }),
    });
    renderDay({ days: [subscribedOnly] });

    expect(
      await screen.findByText(
        "좋아요 · 싫어요는 그 기사 주제의 관심도에 반영되어 다음 다이제스트의 순위를 움직입니다. 싫어요를 누른 기사는 다이제스트를 다시 만들 때 빠집니다.",
      ),
    ).toBeInTheDocument();
    // 선정 이유 표시가 없는 다이제스트라 선정 이유 범례는 없다
    expect(screen.queryByText(/‘관심 반영’은/)).toBeNull();
  });

  it("지난 날짜에서도 그 날짜의 보이는 다이제스트 ID 로 누른다", async () => {
    const yesterday = day({
      date: "2026-11-17",
      previousDate: null,
      nextDate: DATE,
      shown: shown({ digestId: 9 }),
      latest: attempt({ digestId: 9 }),
    });
    const { server } = renderDay({
      date: "2026-11-17",
      days: [yesterday],
      extra: { "PUT /api/feedback/3": () => recorded("DISLIKE") },
    });
    const user = userEvent.setup();
    const buttons = await group(3);
    await waitFor(() =>
      expect(buttons.getByRole("button", { name: "싫어요" })).toBeEnabled(),
    );

    await user.click(buttons.getByRole("button", { name: "싫어요" }));

    await waitFor(() => expect(pressed(buttons, "싫어요")).toBe("true"));
    expect(bodiesOf(server, "PUT", "/api/feedback/3")).toEqual([
      { digestId: 9, value: "DISLIKE" },
    ]);
  });

  it("화면을 떠났다 돌아오면 서버의 현재 상태를 다시 읽어 그린다", async () => {
    const { server, router } = renderDay({
      states: [{ states: [] }, { states: [{ articleId: 1, value: "LIKE" }] }],
    });
    await waitFor(async () =>
      expect(
        (await group(1)).getByRole("button", { name: "좋아요" }),
      ).toBeEnabled(),
    );
    expect(pressed(await group(1), "좋아요")).toBe("false");

    await router.navigate("/elsewhere");
    expect(await screen.findByText("다른 화면")).toBeInTheDocument();
    await router.navigate(`/digests/${DATE}`);

    await waitFor(async () =>
      expect(pressed(await group(1), "좋아요")).toBe("true"),
    );
    expect(statesQueries(server)).toHaveLength(2);
  });

  it("누르기 전에 시작된 상태 다시 읽기가 늦게 와도 누른 값을 덮지 않는다", async () => {
    const stale = deferred(() => json({ states: [] }));
    let statesCalls = 0;
    const { server } = renderDay({
      extra: {
        "GET /api/feedback": (request) => {
          statesCalls += 1;
          return statesCalls === 1
            ? json({ states: [] })
            : stale.handler(request);
        },
        "PUT /api/feedback/1": () => recorded("LIKE"),
      },
    });
    const user = userEvent.setup();
    const buttons = await group(1);
    await waitFor(() =>
      expect(buttons.getByRole("button", { name: "좋아요" })).toBeEnabled(),
    );
    focusManager.setFocused(false);
    focusManager.setFocused(true);
    await waitFor(() => expect(statesQueries(server)).toHaveLength(2));

    await user.click(buttons.getByRole("button", { name: "좋아요" }));
    await waitFor(() => expect(pressed(buttons, "좋아요")).toBe("true"));
    stale.release();

    // 늦게 온 옛 응답이 반영됐다면 눌림이 풀린다 — 응답이 처리될 틈을 준 뒤에 본다
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(pressed(buttons, "좋아요")).toBe("true");
    expect(buttons.getByRole("button", { name: "좋아요" })).toBeEnabled();
  });

  it("상태를 처음 읽는 중인 조회가 누른 기사를 담으면 응답 뒤에 다시 읽어 누를 수 있게 된다", async () => {
    // 기사 2 의 누르기가 받을 수 없는 기사라 다시 읽은 다이제스트는 기사 1 · 3 만 인용한다 — 그 상태를 처음 읽는 동안 기사 1 의 응답이 온다
    const rest = day({
      shown: shown({
        sections: [
          {
            kind: "TOP_STORY",
            title: "주요 뉴스 제목",
            body: "주요 뉴스 요약",
            reason: "SUBSCRIBED",
            citations: [citation(1), citation(3)],
          },
        ],
      }),
    });
    const firstRead = deferred(() => json({ states: [] }));
    const put = deferred(() => recorded("LIKE"));
    let statesCalls = 0;
    const { server } = renderDay({
      days: [day(), rest],
      extra: {
        "GET /api/feedback": (request) => {
          statesCalls += 1;
          if (statesCalls === 1) {
            return json({ states: [] });
          }
          return statesCalls === 2
            ? firstRead.handler(request)
            : json({ states: [{ articleId: 1, value: "LIKE" }] });
        },
        "PUT /api/feedback/1": put.handler,
        "PUT /api/feedback/2": () =>
          json({ result: "NOT_IN_DIGEST", value: null }),
      },
    });
    const user = userEvent.setup();
    const one = await group(1);
    await waitFor(() =>
      expect(one.getByRole("button", { name: "좋아요" })).toBeEnabled(),
    );
    await user.click(one.getByRole("button", { name: "좋아요" }));
    await user.click((await group(2)).getByRole("button", { name: "좋아요" }));
    await waitFor(() =>
      expect(statesQueries(server).map((r) => r.search)).toContain(
        "?articleIds=1&articleIds=3",
      ),
    );

    // 기사 1 의 응답을 먼저 처리하게 한 뒤에 처음 읽기의 응답을 낸다
    put.release();
    await new Promise((resolve) => setTimeout(resolve, 50));
    firstRead.release();

    await waitFor(async () =>
      expect(pressed(await group(1), "좋아요")).toBe("true"),
    );
    expect(
      (await group(3)).getByRole("button", { name: "좋아요" }),
    ).toBeEnabled();
    expect(statesQueries(server).map((r) => r.search)).toEqual([
      "?articleIds=1&articleIds=2&articleIds=3&articleIds=4",
      "?articleIds=1&articleIds=3",
      "?articleIds=1&articleIds=3",
    ]);
  });
});
