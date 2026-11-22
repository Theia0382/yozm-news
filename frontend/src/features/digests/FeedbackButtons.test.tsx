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
  type FeedbackItemsBody,
  shown,
} from "@/features/digests/digestTestKit";
import { nextFeedback } from "@/features/digests/feedbackQueries";
import { type Handler, json, problem } from "@/test/fakeServer";
import { bodiesOf, renderRoutes } from "@/test/renderRoutes";

const DATE = "2026-11-18";

/** 기본 다이제스트(digestTestKit 의 SECTIONS)의 항목 — 주요 뉴스(사건 101 · 102, 기사 1 ~ 3) · 헤드라인(사건 104, 기사 4) */
const STORY = "주요 뉴스 제목 반응";
const HEADLINE = "헤드라인 제목 반응";

afterEach(() => {
  // 창 초점을 흉내 낸 시험이 바꾼 초점 상태를 문서의 상태로 되돌린다
  focusManager.setFocused(undefined);
});

/** 날짜 화면 — 다이제스트 응답은 차례로(마지막 것을 되풀이), 항목 상태 응답도 차례로 */
function renderDay({
  days = [day()],
  states = [{ items: [] }],
  date = DATE,
  extra = {},
}: {
  days?: DigestDay[];
  states?: FeedbackItemsBody[];
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

/** 항목 하나의 버튼 묶음 — 이름은 항목 제목을 담는다 */
async function group(name: string) {
  return within(await screen.findByRole("group", { name }));
}

/** 항목 하나 — 버튼 묶음을 담은 article(항목 제목 · 본문 · 출처 목록) */
async function itemOf(name: string): Promise<HTMLElement> {
  const element = (await screen.findByRole("group", { name })).closest(
    "article",
  );
  if (element === null) {
    throw new Error(`${name} 의 항목(article)이 없다`);
  }
  return element;
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
  it("주요 뉴스 · 헤드라인 항목마다 버튼 묶음이 하나이고 출처 줄 · 개요에는 없다", async () => {
    renderDay();

    for (const name of [STORY, HEADLINE]) {
      const buttons = await group(name);
      await waitFor(() =>
        expect(buttons.getByRole("button", { name: "좋아요" })).toBeEnabled(),
      );
      expect(buttons.getByRole("button", { name: "싫어요" })).toBeEnabled();
    }
    // 사건 둘을 묶은 주요 뉴스 항목도 묶음은 하나다
    expect(
      screen
        .getAllByRole("group")
        .filter((g) => g.tagName !== "DETAILS")
        .map((g) => g.getAttribute("aria-label")),
    ).toEqual([STORY, HEADLINE]);
    expect(screen.getAllByRole("button", { name: "좋아요" })).toHaveLength(2);
    for (const name of ["기사 1", "기사 2", "기사 3", "기사 4"]) {
      const line = screen.getByRole("link", { name }).closest("li");
      expect(within(line as HTMLElement).queryByRole("button")).toBeNull();
    }
    const overview = screen.getByRole("region", { name: "요약" });
    expect(within(overview).queryByRole("button")).toBeNull();
  });

  it("항목에 누른 값을 보이는 다이제스트 ID 로 한 번 묻고 항목 키로 눌림을 그린다", async () => {
    const { server } = renderDay({
      states: [
        {
          items: [
            { eventId: 101, value: "LIKE" },
            { eventId: 104, value: "DISLIKE" },
          ],
        },
      ],
    });

    await waitFor(async () =>
      expect(pressed(await group(STORY), "좋아요")).toBe("true"),
    );
    expect(pressed(await group(STORY), "싫어요")).toBe("false");
    expect(pressed(await group(HEADLINE), "싫어요")).toBe("true");
    expect(pressed(await group(HEADLINE), "좋아요")).toBe("false");
    expect(statesQueries(server).map((r) => r.search)).toEqual([
      "?digestId=11",
    ]);
  });

  it("항목 키가 아닌 사건의 값은 그 항목을 눌림으로 그리지 않는다", async () => {
    // 항목 키는 첫 사건(101)이다 — 둘째 사건(102)의 값은 항목에 누른 값이 아니다
    renderDay({ states: [{ items: [{ eventId: 102, value: "LIKE" }] }] });

    const buttons = await group(STORY);
    await waitFor(() =>
      expect(buttons.getByRole("button", { name: "좋아요" })).toBeEnabled(),
    );
    expect(pressed(buttons, "좋아요")).toBe("false");
    expect(pressed(buttons, "싫어요")).toBe("false");
  });

  it("좋아요를 누르면 항목 키 경로로 보이는 다이제스트 ID 와 함께 보내고 응답 뒤 눌린다", async () => {
    const { server } = renderDay({
      extra: { "PUT /api/feedback/101": () => recorded("LIKE") },
    });
    const user = userEvent.setup();
    const buttons = await group(STORY);
    await waitFor(() =>
      expect(buttons.getByRole("button", { name: "좋아요" })).toBeEnabled(),
    );

    await user.click(buttons.getByRole("button", { name: "좋아요" }));

    await waitFor(() => expect(pressed(buttons, "좋아요")).toBe("true"));
    expect(pressed(buttons, "싫어요")).toBe("false");
    expect(
      server.requests
        .filter((r) => r.method === "PUT")
        .map((r) => [r.path, JSON.parse(r.body) as unknown]),
    ).toEqual([["/api/feedback/101", { digestId: 11, value: "LIKE" }]]);
  });

  it("눌린 좋아요를 다시 누르면 없음으로 되돌린다", async () => {
    const { server } = renderDay({
      states: [{ items: [{ eventId: 104, value: "LIKE" }] }],
      extra: { "PUT /api/feedback/104": () => recorded("NONE") },
    });
    const user = userEvent.setup();
    const buttons = await group(HEADLINE);
    await waitFor(() => expect(pressed(buttons, "좋아요")).toBe("true"));
    expect(buttons.getByRole("button", { name: "좋아요" })).toHaveAttribute(
      "title",
      "누르면 되돌립니다",
    );

    await user.click(buttons.getByRole("button", { name: "좋아요" }));

    await waitFor(() => expect(pressed(buttons, "좋아요")).toBe("false"));
    expect(pressed(buttons, "싫어요")).toBe("false");
    expect(bodiesOf(server, "PUT", "/api/feedback/104")).toEqual([
      { digestId: 11, value: "NONE" },
    ]);
  });

  it("좋아요인 항목의 싫어요를 누르면 바로 싫어요로 바뀐다", async () => {
    const { server } = renderDay({
      states: [{ items: [{ eventId: 104, value: "LIKE" }] }],
      extra: { "PUT /api/feedback/104": () => recorded("DISLIKE") },
    });
    const user = userEvent.setup();
    const buttons = await group(HEADLINE);
    await waitFor(() => expect(pressed(buttons, "좋아요")).toBe("true"));

    await user.click(buttons.getByRole("button", { name: "싫어요" }));

    await waitFor(() => expect(pressed(buttons, "싫어요")).toBe("true"));
    expect(pressed(buttons, "좋아요")).toBe("false");
    expect(bodiesOf(server, "PUT", "/api/feedback/104")).toEqual([
      { digestId: 11, value: "DISLIKE" },
    ]);
  });

  it("같은 기사를 인용한 항목 둘은 따로 누르고 누른 항목만 바뀐다", async () => {
    const shared = day({
      shown: shown({
        sections: [
          {
            kind: "TOP_STORY",
            title: "첫 주요 뉴스",
            body: "요약 하나",
            reason: "SUBSCRIBED",
            storylineId: null,
            eventIds: [201],
            citations: [citation(1), citation(2)],
          },
          {
            kind: "TOP_STORY",
            title: "둘째 주요 뉴스",
            body: "요약 둘",
            reason: "SUBSCRIBED",
            storylineId: null,
            eventIds: [202],
            citations: [citation(1)],
          },
        ],
      }),
    });
    const put = deferred(() => recorded("LIKE"));
    const { server } = renderDay({
      days: [shared],
      extra: { "PUT /api/feedback/202": put.handler },
    });
    const user = userEvent.setup();
    const first = await group("첫 주요 뉴스 반응");
    const second = await group("둘째 주요 뉴스 반응");
    await waitFor(() =>
      expect(second.getByRole("button", { name: "좋아요" })).toBeEnabled(),
    );

    await user.click(second.getByRole("button", { name: "좋아요" }));

    await waitFor(() =>
      expect(second.getByRole("button", { name: "좋아요" })).toBeDisabled(),
    );
    expect(first.getByRole("button", { name: "좋아요" })).toBeEnabled();
    put.release();
    await waitFor(() => expect(pressed(second, "좋아요")).toBe("true"));
    expect(pressed(first, "좋아요")).toBe("false");
    expect(
      server.requests.filter((r) => r.method === "PUT").map((r) => r.path),
    ).toEqual(["/api/feedback/202"]);
  });

  it("응답을 기다리는 동안 그 항목의 버튼만 잠긴다", async () => {
    const put = deferred(() => recorded("LIKE"));
    renderDay({ extra: { "PUT /api/feedback/101": put.handler } });
    const user = userEvent.setup();
    const story = await group(STORY);
    const headline = await group(HEADLINE);
    await waitFor(() =>
      expect(story.getByRole("button", { name: "좋아요" })).toBeEnabled(),
    );

    await user.click(story.getByRole("button", { name: "좋아요" }));

    await waitFor(() =>
      expect(story.getByRole("button", { name: "좋아요" })).toBeDisabled(),
    );
    expect(story.getByRole("button", { name: "싫어요" })).toBeDisabled();
    expect(headline.getByRole("button", { name: "좋아요" })).toBeEnabled();
    expect(headline.getByRole("button", { name: "싫어요" })).toBeEnabled();
    // 응답 전에는 눌림으로 그리지 않는다 — 서버 응답 뒤에 반영한다
    expect(pressed(story, "좋아요")).toBe("false");
    put.release();
    await waitFor(() => expect(pressed(story, "좋아요")).toBe("true"));
  });

  it("받을 수 없는 항목이면 그 항목에 알리고 다이제스트를 다시 읽는다 — 상태는 그대로다", async () => {
    const { server } = renderDay({
      extra: {
        "PUT /api/feedback/101": () =>
          json({ result: "NOT_IN_DIGEST", value: null }),
      },
    });
    const user = userEvent.setup();
    const buttons = await group(STORY);
    await waitFor(() =>
      expect(buttons.getByRole("button", { name: "좋아요" })).toBeEnabled(),
    );
    const daysBefore = server.requests.filter(
      (r) => r.path === `/api/digests/${DATE}`,
    ).length;

    await user.click(buttons.getByRole("button", { name: "좋아요" }));

    const item = await itemOf(STORY);
    expect((await within(item).findByRole("alert")).textContent).toBe(
      "이 항목에는 더 반응을 남길 수 없습니다.",
    );
    expect(
      within(await itemOf(HEADLINE)).queryByRole("alert"),
    ).not.toBeInTheDocument();
    await waitFor(() =>
      expect(
        server.requests.filter((r) => r.path === `/api/digests/${DATE}`),
      ).toHaveLength(daysBefore + 1),
    );
    expect(pressed(buttons, "좋아요")).toBe("false");
  });

  it("다시 읽은 다이제스트가 그 항목을 빼면 항목과 버튼 묶음이 사라진다", async () => {
    const withoutStory = day({
      shown: shown({
        sections: [
          {
            kind: "HEADLINE",
            title: "헤드라인 제목",
            body: "한 줄 코멘트",
            reason: "INTEREST",
            storylineId: null,
            eventIds: [104],
            citations: [citation(4)],
          },
        ],
      }),
    });
    const { server } = renderDay({
      days: [day(), withoutStory],
      extra: {
        "PUT /api/feedback/101": () =>
          json({ result: "NOT_IN_DIGEST", value: null }),
      },
    });
    const user = userEvent.setup();
    const buttons = await group(STORY);
    await waitFor(() =>
      expect(buttons.getByRole("button", { name: "좋아요" })).toBeEnabled(),
    );

    await user.click(buttons.getByRole("button", { name: "좋아요" }));

    await waitFor(() =>
      expect(screen.queryByRole("group", { name: STORY })).toBeNull(),
    );
    expect(screen.queryByRole("link", { name: "기사 1" })).toBeNull();
    expect(await group(HEADLINE)).toBeDefined();
    // 같은 다이제스트라 항목 상태는 같은 다이제스트 ID 로만 묻는다
    expect(new Set(statesQueries(server).map((r) => r.search))).toStrictEqual(
      new Set(["?digestId=11"]),
    );
  });

  it("요청 제한에 걸리면 이 계정의 요청 제한 안내를 남은 시간과 함께 그 항목에 보인다", async () => {
    renderDay({
      extra: {
        "PUT /api/feedback/101": () =>
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
    const buttons = await group(STORY);
    await waitFor(() =>
      expect(buttons.getByRole("button", { name: "좋아요" })).toBeEnabled(),
    );

    await user.click(buttons.getByRole("button", { name: "좋아요" }));

    const item = await itemOf(STORY);
    expect((await within(item).findByRole("alert")).textContent).toBe(
      "이 계정의 요청이 너무 많아 잠시 막혔습니다. 2분 뒤에 다시 시도해 주세요.",
    );
    expect(pressed(buttons, "좋아요")).toBe("false");
  });

  it("그 밖의 실패면 남기지 못했다고 알리고 상태는 그대로다", async () => {
    renderDay({
      states: [{ items: [{ eventId: 101, value: "DISLIKE" }] }],
      extra: { "PUT /api/feedback/101": () => problem(500, "서버 오류") },
    });
    const user = userEvent.setup();
    const buttons = await group(STORY);
    await waitFor(() => expect(pressed(buttons, "싫어요")).toBe("true"));

    await user.click(buttons.getByRole("button", { name: "좋아요" }));

    const item = await itemOf(STORY);
    expect((await within(item).findByRole("alert")).textContent).toBe(
      "반응을 남기지 못했습니다. 잠시 뒤 다시 눌러 주세요.",
    );
    expect(pressed(buttons, "싫어요")).toBe("true");
    expect(pressed(buttons, "좋아요")).toBe("false");
  });

  it("상태를 읽기 전에는 버튼을 누를 수 없다", async () => {
    const states = deferred(() => json({ items: [] }));
    renderDay({ extra: { "GET /api/feedback": states.handler } });

    const buttons = await group(STORY);
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
    expect(buttons).toHaveLength(4);
    for (const button of buttons) {
      expect(button).toBeDisabled();
    }
  });

  it("반응을 받는 항목이 없으면 상태를 묻지 않고 버튼 · 효과 안내도 없다", async () => {
    const overviewOnly = day({
      shown: shown({
        sections: [
          {
            kind: "OVERVIEW",
            title: null,
            body: "오늘의 흐름",
            reason: null,
            storylineId: null,
            eventIds: [],
            citations: [],
          },
        ],
      }),
    });
    const { server } = renderDay({ days: [overviewOnly] });

    expect(await screen.findByText("오늘의 흐름")).toBeInTheDocument();
    expect(statesQueries(server)).toHaveLength(0);
    expect(screen.queryByRole("group")).toBeNull();
    expect(screen.queryByText(/좋아요 · 싫어요는/)).toBeNull();
  });

  it("섹션이 없으면 상태를 묻지 않는다", async () => {
    const { server } = renderDay({
      days: [day({ shown: shown({ sections: [] }) })],
    });

    expect(await screen.findByRole("note")).toBeInTheDocument();
    expect(statesQueries(server)).toHaveLength(0);
    expect(screen.queryByRole("group")).toBeNull();
  });

  it("항목이 있으면 효과 안내 한 줄이 선정 이유 표시와 무관하게 보인다", async () => {
    const subscribedOnly = day({
      shown: shown({
        sections: [
          {
            kind: "TOP_STORY",
            title: "주요 뉴스 제목",
            body: "주요 뉴스 요약",
            reason: "SUBSCRIBED",
            storylineId: null,
            eventIds: [101],
            citations: [citation(1)],
          },
        ],
      }),
    });
    renderDay({ days: [subscribedOnly] });

    expect(
      await screen.findByText(
        "좋아요 · 싫어요는 그 항목의 주제나 흐름의 관심도에 반영되어 다음 다이제스트의 순위를 움직입니다. 싫어요를 누른 항목의 소식은 뒤에 만드는 다이제스트에서 빠지고, 그 흐름의 다음 소식도 빠질 수 있습니다.",
      ),
    ).toBeInTheDocument();
    // 선정 이유 표시가 없는 다이제스트라 선정 이유 범례는 없다
    expect(screen.queryByText(/‘관심 반영’은/)).toBeNull();
  });

  it("지난 날짜에서도 그 날짜의 보이는 다이제스트 ID 로 묻고 누른다", async () => {
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
      extra: { "PUT /api/feedback/104": () => recorded("DISLIKE") },
    });
    const user = userEvent.setup();
    const buttons = await group(HEADLINE);
    await waitFor(() =>
      expect(buttons.getByRole("button", { name: "싫어요" })).toBeEnabled(),
    );

    await user.click(buttons.getByRole("button", { name: "싫어요" }));

    await waitFor(() => expect(pressed(buttons, "싫어요")).toBe("true"));
    expect(bodiesOf(server, "PUT", "/api/feedback/104")).toEqual([
      { digestId: 9, value: "DISLIKE" },
    ]);
    expect(statesQueries(server).map((r) => r.search)).toEqual(["?digestId=9"]);
  });

  it("같은 사건이 든 다른 날짜의 항목은 이 항목에 누른 값을 보이지 않는다", async () => {
    // 두 날짜의 주요 뉴스 항목이 같은 사건(101)을 항목 키로 갖는다 — 다른 날짜의 항목은 그 항목에 누른 값만 보인다
    const yesterday = day({
      date: "2026-11-17",
      previousDate: null,
      nextDate: DATE,
      shown: shown({ digestId: 9 }),
      latest: attempt({ digestId: 9 }),
    });
    const secondRead = deferred(() => json({ items: [] }));
    let yesterdayReads = 0;
    const { server, router } = renderRoutes(
      [{ path: "/digests/:date", element: <DigestDayPage /> }],
      "/digests/2026-11-17",
      {
        "GET /api/digests/2026-11-17": () => json(yesterday),
        [`GET /api/digests/${DATE}`]: () => json(day()),
        "GET /api/feedback": (request) => {
          if (new URL(request.url).searchParams.get("digestId") === "11") {
            return json({ items: [] });
          }
          yesterdayReads += 1;
          return yesterdayReads === 1
            ? json({ items: [] })
            : secondRead.handler(request);
        },
        "PUT /api/feedback/101": () => recorded("LIKE"),
      },
    );
    const user = userEvent.setup();
    await waitFor(async () =>
      expect(
        (await group(STORY)).getByRole("button", { name: "좋아요" }),
      ).toBeEnabled(),
    );

    await router.navigate(`/digests/${DATE}`);
    await screen.findByRole("heading", {
      level: 1,
      name: "2026. 11. 18. 다이제스트",
    });
    const today = await group(STORY);
    await waitFor(() =>
      expect(today.getByRole("button", { name: "좋아요" })).toBeEnabled(),
    );
    await user.click(today.getByRole("button", { name: "좋아요" }));
    await waitFor(() => expect(pressed(today, "좋아요")).toBe("true"));

    // 다시 읽기를 붙잡아 두어 캐시된 그 날짜의 항목 상태가 그대로 보이게 한다
    await router.navigate("/digests/2026-11-17");
    await screen.findByRole("heading", {
      level: 1,
      name: "2026. 11. 17. 다이제스트",
    });
    await waitFor(() => expect(yesterdayReads).toBe(2));
    const previous = await group(STORY);
    expect(previous.getByRole("button", { name: "좋아요" })).toBeEnabled();
    expect(pressed(previous, "좋아요")).toBe("false");
    secondRead.release();
    expect(
      server.requests.filter((r) => r.method === "PUT").map((r) => r.path),
    ).toEqual(["/api/feedback/101"]);
  });

  it("화면을 떠났다 돌아오면 서버의 항목 상태를 다시 읽어 그린다", async () => {
    const { server, router } = renderDay({
      states: [{ items: [] }, { items: [{ eventId: 101, value: "LIKE" }] }],
    });
    await waitFor(async () =>
      expect(
        (await group(STORY)).getByRole("button", { name: "좋아요" }),
      ).toBeEnabled(),
    );
    expect(pressed(await group(STORY), "좋아요")).toBe("false");

    await router.navigate("/elsewhere");
    expect(await screen.findByText("다른 화면")).toBeInTheDocument();
    await router.navigate(`/digests/${DATE}`);

    await waitFor(async () =>
      expect(pressed(await group(STORY), "좋아요")).toBe("true"),
    );
    expect(statesQueries(server)).toHaveLength(2);
  });

  it("누르기 전에 시작된 상태 다시 읽기가 늦게 와도 누른 값을 덮지 않는다", async () => {
    const stale = deferred(() => json({ items: [] }));
    let statesCalls = 0;
    const { server } = renderDay({
      extra: {
        "GET /api/feedback": (request) => {
          statesCalls += 1;
          return statesCalls === 1
            ? json({ items: [] })
            : stale.handler(request);
        },
        "PUT /api/feedback/101": () => recorded("LIKE"),
      },
    });
    const user = userEvent.setup();
    const buttons = await group(STORY);
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
});
