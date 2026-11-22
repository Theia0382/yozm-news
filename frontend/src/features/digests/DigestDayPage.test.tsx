import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { DigestDayPage } from "@/features/digests/DigestDayPage";
import type { DigestDay } from "@/features/digests/digestQueries";
import {
  attempt,
  citation,
  day,
  noFeedback,
  SECTIONS,
  shown,
} from "@/features/digests/digestTestKit";
import {
  subscriptionHandlers,
  trackingStates,
} from "@/features/storylines/storylineTestKit";
import { type Handler, json, problem } from "@/test/fakeServer";
import { renderRoutes } from "@/test/renderRoutes";

function renderDay(
  response: DigestDay | Response,
  date = "2026-11-18",
  handlers: Record<string, Handler> = {},
) {
  return renderRoutes(
    [{ path: "/digests/:date", element: <DigestDayPage /> }],
    `/digests/${date}`,
    {
      [`GET /api/digests/${date}`]: () =>
        response instanceof Response ? response : json(response),
      "GET /api/feedback": () => json(noFeedback()),
      ...handlers,
    },
  );
}

/** 주요 뉴스(흐름 7) · 헤드라인(흐름 8) · 흐름 없는 헤드라인 — 개요는 SECTIONS 의 것 */
function flowSections(): DigestDay {
  return day({
    shown: shown({
      sections: [
        SECTIONS[0]!,
        { ...SECTIONS[1]!, storylineId: 7 },
        { ...SECTIONS[2]!, storylineId: 8 },
        {
          kind: "HEADLINE",
          title: "흐름 없는 헤드라인",
          body: null,
          reason: "SUBSCRIBED",
          storylineId: null,
          userStoryline: null,
          eventIds: [105],
          citations: [citation(5)],
        },
      ],
    }),
  });
}

describe("DigestDayPage", () => {
  it("보이는 다이제스트의 요약 · 주요 뉴스 · 헤드라인 항목을 출처 기사와 함께 그린다", async () => {
    renderDay(day());

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "2026. 11. 18. 다이제스트",
      }),
    ).toBeInTheDocument();
    // 종합문은 글자로만 — HTML 로 해석하지 않는다
    expect(screen.getByText("오늘의 흐름 <b>굵게</b>")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 3, name: /주요 뉴스 제목/ }),
    ).toBeInTheDocument();
    expect(screen.getByText("주요 뉴스 요약")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "기사 1" });
    expect(link).toHaveAttribute("href", "https://news.example/1");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link.closest("li")).toHaveTextContent(
      "기사 1 · 한국일보 · 2026. 11. 18. 오전 7:30 GMT+9",
    );
    // 주요 뉴스 항목의 출처 기사 셋이 한 목록이다
    expect(link.closest("ul")?.querySelectorAll("li")).toHaveLength(3);
    // 헤드라인 항목 — 제목 · 코멘트 · 출처 기사
    expect(
      screen.getByRole("heading", { level: 3, name: /헤드라인 제목/ }),
    ).toBeInTheDocument();
    expect(screen.getByText("한 줄 코멘트")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "기사 4" })).toBeInTheDocument();
    expect(
      screen.getByText(
        "기간 2026. 11. 17. 오전 7:00 GMT+9 ~ 2026. 11. 18. 오전 7:00 GMT+9",
      ),
    ).toBeInTheDocument();
  });

  it("좋아요 · 싫어요는 주요 뉴스 · 헤드라인 항목 머리마다 한 벌이고 출처 기사 줄에는 없다", async () => {
    renderDay(day());

    const story = await screen.findByRole("group", {
      name: "주요 뉴스 제목 반응",
    });
    const headline = screen.getByRole("group", { name: "헤드라인 제목 반응" });
    // 출처 목록의 접기(details — 역할 group)는 버튼 묶음이 아니다
    expect(
      screen.getAllByRole("group").filter((g) => g.tagName !== "DETAILS"),
    ).toEqual([story, headline]);
    // 묶음은 항목 제목과 같은 항목(article) 안, 출처 목록 밖에 있다
    expect(story.closest("li")).toBeNull();
    expect(story.closest("article")).toBe(
      screen
        .getByRole("heading", { level: 3, name: /주요 뉴스 제목/ })
        .closest("article"),
    );
    for (const name of ["기사 1", "기사 2", "기사 3", "기사 4"]) {
      const line = screen.getByRole("link", { name }).closest("li");
      expect(line).not.toBeNull();
      expect(within(line as HTMLElement).queryByRole("button")).toBeNull();
    }
  });

  it("항목의 출처 기사 목록은 기본으로 접혀 있고 요약 줄을 누르면 펼쳐진다", async () => {
    renderDay(day());

    const heading = await screen.findByRole("heading", {
      level: 3,
      name: /주요 뉴스 제목/,
    });
    const item = within(heading.closest("article") as HTMLElement);
    const summary = item.getByText("출처 기사 3건");
    expect(item.getByRole("link", { name: "기사 1" })).not.toBeVisible();

    fireEvent.click(summary);

    expect(item.getByRole("link", { name: "기사 1" })).toBeVisible();
  });

  it("흐름이 있는 항목에만 흐름 추적 버튼을 두고, 상태는 그 다이제스트의 흐름을 한 번에 물어 읽는다", async () => {
    const { server } = renderDay(
      flowSections(),
      "2026-11-18",
      subscriptionHandlers([7], [7, 8]),
    );

    // 버튼의 이름은 항목 제목을 담는다 — 주요 뉴스는 추적 중, 헤드라인은 아니다
    const tracked = await screen.findByRole("button", {
      name: "주요 뉴스 제목 추적 중",
    });
    expect(tracked).toHaveAttribute("aria-pressed", "true");
    const untracked = screen.getByRole("button", {
      name: "헤드라인 제목 흐름 추적",
    });
    expect(untracked).toHaveAttribute("aria-pressed", "false");
    await waitFor(() => expect(untracked).toBeEnabled());
    // 흐름 없는 항목 · 개요에는 없다
    expect(
      screen.queryByRole("button", {
        name: /^흐름 없는 헤드라인 (흐름 추적|추적 중)$/,
      }),
    ).toBeNull();
    expect(
      within(screen.getByRole("region", { name: "요약" })).queryByRole(
        "button",
      ),
    ).toBeNull();
    const stateReads = () =>
      server.requests.filter(
        (r) => r.path === "/api/storyline-subscriptions/states",
      );
    expect(stateReads().map((r) => r.search)).toEqual(["?ids=7&ids=8"]);

    // 항목에서 추적하면 그 흐름의 ID 로 보내고 상태를 다시 읽는다
    await userEvent.click(untracked);
    expect(
      await screen.findByRole("button", { name: "헤드라인 제목 추적 중" }),
    ).toBeInTheDocument();
    expect(
      server.requests
        .filter((r) => r.method !== "GET")
        .map((r) => `${r.method} ${r.path}`),
    ).toEqual(["PUT /api/storyline-subscriptions/8"]);
    expect(stateReads()).toHaveLength(2);
  });

  it("두 항목이 같은 흐름이면 한쪽에서 추적할 때 다른 쪽도 추적 중이 된다", async () => {
    // 서버는 같은 사용자 스토리라인의 스토리라인을 함께 추적 중으로 답한다 — 7 과 8 이 한 흐름
    let on = false;
    renderDay(flowSections(), "2026-11-18", {
      "GET /api/storyline-subscriptions/states": (request) =>
        trackingStates(request, () => on),
      "PUT /api/storyline-subscriptions/8": () => {
        on = true;
        return json({ result: "SUBSCRIBED", storylineId: 8, limit: null });
      },
    });
    const later = await screen.findByRole("button", {
      name: "헤드라인 제목 흐름 추적",
    });
    await waitFor(() => expect(later).toBeEnabled());

    await userEvent.click(later);

    expect(
      await screen.findByRole("button", { name: "주요 뉴스 제목 추적 중" }),
    ).toHaveAttribute("aria-pressed", "true");
    expect(
      screen.getByRole("button", { name: "헤드라인 제목 추적 중" }),
    ).toBeInTheDocument();
  });

  it("흐름 추적의 상한은 항목 안에 알리고 추적 중인 흐름으로 가는 링크를 둔다", async () => {
    renderDay(flowSections(), "2026-11-18", {
      "GET /api/storyline-subscriptions/states": (request) =>
        trackingStates(request, () => false),
      "PUT /api/storyline-subscriptions/7": () =>
        json({ result: "LIMIT_REACHED", storylineId: null, limit: 100 }),
    });
    const button = await screen.findByRole("button", {
      name: "주요 뉴스 제목 흐름 추적",
    });
    await waitFor(() => expect(button).toBeEnabled());

    await userEvent.click(button);

    const limited = await screen.findByRole("alert");
    expect(limited).toHaveTextContent(
      "흐름 추적은 100개까지입니다. 추적 중인 흐름에서 정리해 주세요.",
    );
    expect(
      within(limited).getByRole("link", { name: "추적 중인 흐름" }),
    ).toHaveAttribute("href", "/storylines/subscriptions");
    // 알림은 그 항목 안에 있다
    expect(limited.closest("article")).toHaveTextContent("주요 뉴스 제목");
  });

  it("추적 상태를 읽지 못하면 버튼을 누를 수 없고 그렇게 알린다", async () => {
    renderDay(flowSections(), "2026-11-18", {
      "GET /api/storyline-subscriptions/states": () => problem(500, "오류"),
    });

    expect(
      await screen.findByText(
        "흐름 추적 상태를 불러오지 못해 지금은 추적을 바꿀 수 없습니다.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "주요 뉴스 제목 흐름 추적" }),
    ).toBeDisabled();
  });

  it("흐름이 있는 항목이 없으면 추적 상태를 묻지 않는다", async () => {
    const { server } = renderDay(day());

    await screen.findByRole("heading", { level: 3, name: /주요 뉴스 제목/ });
    expect(
      server.requests.filter((r) =>
        r.path.startsWith("/api/storyline-subscriptions"),
      ),
    ).toHaveLength(0);
  });

  it("개요에는 좋아요 · 싫어요가 없다", async () => {
    renderDay(day());

    const overview = await screen.findByRole("region", { name: "요약" });
    expect(within(overview).queryByRole("group")).toBeNull();
    expect(within(overview).queryByRole("button")).toBeNull();
  });

  it("항목을 묶은 흐름이 정리돼 있으면 제목 아래에 정리의 제목을 개인 흐름 화면 링크로 두고, 없으면 두지 않는다", async () => {
    renderDay(
      day({
        shown: shown({
          sections: [
            {
              kind: "HEADLINE",
              title: "정리된 흐름의 소식",
              body: null,
              reason: "SUBSCRIBED",
              storylineId: null,
              userStoryline: { id: 3, title: "의대 정원 갈등" },
              eventIds: [105],
              citations: [citation(5)],
            },
            {
              kind: "HEADLINE",
              title: "정리 없는 소식",
              body: null,
              reason: "SUBSCRIBED",
              storylineId: null,
              userStoryline: null,
              eventIds: [106],
              citations: [citation(6)],
            },
          ],
        }),
      }),
    );

    const linked = await screen.findByRole("heading", {
      level: 3,
      name: "정리된 흐름의 소식",
    });
    const flow = screen.getByRole("link", { name: "의대 정원 갈등" });
    expect(flow).toHaveAttribute("href", "/user-storylines/3");
    // 링크는 제목 요소 밖이다 — 낭독되는 제목 이름에 섞이지 않는다
    expect(linked).not.toContainElement(flow);
    expect(linked.parentElement).toContainElement(flow);
    expect(flow.closest("p")).toHaveTextContent("흐름 의대 정원 갈등");
    // 정리 없는 항목에는 흐름 줄이 없다 — 화면의 흐름 링크는 하나다
    const plain = screen.getByRole("heading", {
      level: 3,
      name: "정리 없는 소식",
    });
    expect(plain.parentElement).not.toHaveTextContent("흐름");
    expect(
      screen
        .getAllByRole("link")
        .filter((link) =>
          link.getAttribute("href")?.startsWith("/user-storylines/"),
        ),
    ).toHaveLength(1);
  });

  it("선정 이유 배지는 항목에 하나이고 구독 주제가 아닌 항목에만 두며 범례를 둔다", async () => {
    renderDay(
      day({
        shown: shown({
          sections: [
            ...SECTIONS,
            {
              kind: "HEADLINE",
              title: "구독 헤드라인",
              body: null,
              reason: "SUBSCRIBED",
              storylineId: null,
              userStoryline: null,
              eventIds: [105],
              citations: [citation(5)],
            },
          ],
        }),
      }),
    );

    const story = await screen.findByRole("heading", {
      level: 3,
      name: /주요 뉴스 제목/,
    });
    const headline = screen.getByRole("heading", {
      level: 3,
      name: /헤드라인 제목/,
    });
    const subscribed = screen.getByRole("heading", {
      level: 3,
      name: "구독 헤드라인",
    });
    expect(story).toHaveTextContent("큰 화제");
    // 낭독되는 제목 이름은 제목과 배지 사이에 구분 글자가 있다
    expect(
      screen.getByRole("heading", {
        level: 3,
        name: /^주요 뉴스 제목\s*·\s*큰 화제$/,
      }),
    ).toBe(story);
    expect(
      screen.getByRole("heading", {
        level: 3,
        name: /^헤드라인 제목\s*·\s*관심 반영$/,
      }),
    ).toBe(headline);
    expect(headline).toHaveTextContent("관심 반영");
    expect(within(subscribed).queryByText("구독 주제")).not.toBeInTheDocument();
    // 배지는 항목에만 — 출처 기사 줄에는 없다
    expect(screen.getAllByText("큰 화제")).toHaveLength(1);
    expect(
      screen.getByRole("link", { name: "기사 2" }).closest("li"),
    ).not.toHaveTextContent("큰 화제");
    // 관심 반영은 주제 · 흐름 관심으로 앞선 소식과, 구독하지 않은 주제의 따라가는 흐름 소식을 함께 말한다
    expect(
      screen.getByText(
        /‘관심 반영’은 좋아요 · 구독 · 흐름 추적으로 관심이 높아진 주제나 흐름이라 앞 순위에 든 소식, 또는 추적하거나 관심이 높은 흐름의 소식이라 구독하지 않은 주제여도/,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/표시가 없는 항목은 구독한 주제에서 왔습니다/),
    ).toBeInTheDocument();
  });

  it("구독 주제 항목만 있으면 범례를 두지 않는다", async () => {
    renderDay(
      day({
        shown: shown({
          sections: [
            {
              kind: "HEADLINE",
              title: "구독 헤드라인",
              body: null,
              reason: "SUBSCRIBED",
              storylineId: null,
              userStoryline: null,
              eventIds: [101],
              citations: [citation(1)],
            },
          ],
        }),
      }),
    );

    expect(
      await screen.findByRole("link", { name: "기사 1" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/표시가 없는 항목은 구독한 주제에서 왔습니다/),
    ).not.toBeInTheDocument();
  });

  it("보이는 다이제스트의 모델 · 토큰을 보이고 시도가 하나면 날짜 합은 보이지 않는다", async () => {
    renderDay(day());

    expect(
      await screen.findByText(
        /모델 gpt-test · 후보 30건 · 입력 토큰 1,200 · 출력 토큰 300/,
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/이 날짜에/)).not.toBeInTheDocument();
  });

  it("시도가 여럿이면 그 날짜 모든 시도의 토큰 합을 함께 보인다", async () => {
    renderDay(
      day({
        latest: attempt({
          digestId: 12,
          status: "FAILED",
          failure: "NO_CITATION",
          cause: "OUTPUT",
        }),
        usage: { attempts: 2, inputTokens: 1700, outputTokens: 300 },
      }),
    );

    expect(
      await screen.findByText(
        "이 날짜에 2번 시도했고, 다시 만들거나 실패한 것까지 모두 입력 토큰 1,700 · 출력 토큰 300을 썼습니다.",
      ),
    ).toBeInTheDocument();
  });

  it("보이는 다이제스트 없이 토큰을 쓴 실패도 그 날짜의 합을 보인다", async () => {
    renderDay(
      day({
        shown: null,
        latest: attempt({
          status: "FAILED",
          failure: "OUTPUT_TRUNCATED",
          cause: "OUTPUT",
        }),
        usage: { attempts: 1, inputTokens: 900, outputTokens: 4000 },
      }),
    );

    expect(
      await screen.findByText(
        "이 날짜에 1번 시도했고, 다시 만들거나 실패한 것까지 모두 입력 토큰 900 · 출력 토큰 4,000을 썼습니다.",
      ),
    ).toBeInTheDocument();
  });

  it("보이는 다이제스트 없이 만드는 중이면 그렇게 알린다", async () => {
    renderDay(
      day({
        shown: null,
        latest: attempt({ status: "RUNNING", completedAt: null }),
        usage: { attempts: 1, inputTokens: 0, outputTokens: 0 },
      }),
    );

    expect(await screen.findByRole("status")).toHaveTextContent(
      "다이제스트를 만드는 중입니다. 다 만들면 이 화면에 나타납니다.",
    );
    expect(screen.queryByText(/토큰/)).not.toBeInTheDocument();
  });

  it("보이는 다이제스트를 다시 만드는 중이면 지금 다이제스트와 함께 알린다", async () => {
    renderDay(
      day({
        latest: attempt({
          digestId: 12,
          status: "SCHEDULED",
          completedAt: null,
        }),
        usage: { attempts: 2, inputTokens: 1200, outputTokens: 300 },
      }),
    );

    expect(await screen.findByRole("status")).toHaveTextContent(
      "다시 만드는 중입니다. 다 만들 때까지 지금 다이제스트가 보입니다.",
    );
    expect(screen.getByRole("link", { name: "기사 1" })).toBeInTheDocument();
  });

  it("후보가 없었던 날짜와 다시 만든 기간에 후보가 없었던 날짜를 가른다", async () => {
    renderDay(
      day({
        shown: null,
        latest: attempt({ status: "EMPTY" }),
        usage: { attempts: 1, inputTokens: 0, outputTokens: 0 },
      }),
    );
    expect(await screen.findByRole("status")).toHaveTextContent(
      "이 기간에는 다이제스트에 넣을 기사가 없었습니다.",
    );
  });

  it("다시 만든 기간에 후보가 없었으면 앞서 만든 다이제스트를 보인다고 알린다", async () => {
    renderDay(
      day({
        latest: attempt({ digestId: 12, status: "EMPTY" }),
        usage: { attempts: 2, inputTokens: 1200, outputTokens: 300 },
      }),
    );
    expect(await screen.findByRole("status")).toHaveTextContent(
      "다시 만든 기간에 넣을 기사가 없어 앞서 만든 다이제스트를 보여 줍니다.",
    );
  });

  it("키 실패는 무슨 일 · 할 일과 함께 키 화면으로 안내한다", async () => {
    renderDay(
      day({
        shown: null,
        latest: attempt({
          status: "FAILED",
          failure: "KEY_INVALID",
          cause: "KEY",
        }),
        usage: { attempts: 1, inputTokens: 0, outputTokens: 0 },
      }),
    );

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("다이제스트를 만들지 못했습니다");
    expect(alert).toHaveTextContent(
      "공급자가 등록한 LLM 키를 받아 주지 않았습니다.",
    );
    expect(alert).toHaveTextContent(
      "LLM 키를 쓸 수 없었습니다. 키를 확인하거나 다시 등록하세요.",
    );
    expect(
      within(alert).getByRole("link", { name: "LLM 키 화면에서 확인하기" }),
    ).toHaveAttribute("href", "/keys");
  });

  it("출력 실패는 관심 설정의 모델 절로 잇고, 다시 만들기 실패면 앞 다이제스트를 보인다고 알린다", async () => {
    renderDay(
      day({
        latest: attempt({
          digestId: 12,
          status: "FAILED",
          failure: "OUTPUT_REFUSED",
          cause: "OUTPUT",
        }),
        usage: { attempts: 2, inputTokens: 1500, outputTokens: 310 },
      }),
    );

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(
      "다시 만들지 못해 앞서 만든 다이제스트를 보여 줍니다.",
    );
    expect(alert).toHaveTextContent("모델이 다이제스트 작성을 거절했습니다.");
    // 날짜 화면에는 다시 만들기 버튼이 없다 — 할 일은 홈(오늘 다이제스트)을 가리킨다
    expect(alert).toHaveTextContent(
      "오늘 다이제스트에서 다시 만들거나 관심 설정에서 다른 모델을 골라 보세요.",
    );
    expect(within(alert).getAllByRole("link")).toHaveLength(1);
    expect(
      within(alert).getByRole("link", { name: "관심 설정에서 모델 고르기" }),
    ).toHaveAttribute("href", "/interests#interest-model");
    expect(screen.getByRole("link", { name: "기사 1" })).toBeInTheDocument();
  });

  it("잠시 뒤 다시 할 실패는 가 볼 화면이 없어 링크를 두지 않는다", async () => {
    renderDay(
      day({
        shown: null,
        latest: attempt({
          status: "FAILED",
          failure: "PROVIDER_UNAVAILABLE",
          cause: "TEMPORARY",
        }),
        usage: { attempts: 1, inputTokens: 0, outputTokens: 0 },
      }),
    );

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(
      "공급자가 여러 번 다시 보내도 답하지 못했습니다.",
    );
    expect(within(alert).queryByRole("link")).not.toBeInTheDocument();
  });

  it("인용한 기사가 모두 지워진 다이제스트는 따로 알린다", async () => {
    renderDay(day({ shown: shown({ sections: [] }) }));

    expect(await screen.findByRole("note")).toHaveTextContent(
      "이 다이제스트가 인용한 기사가 모두 지워져 보일 내용이 없습니다.",
    );
  });

  it("이전 · 다음 날짜로 옮겨 간다", async () => {
    renderDay(day({ previousDate: "2026-11-17", nextDate: "2026-11-19" }));

    expect(
      await screen.findByRole("link", { name: "이전 다이제스트" }),
    ).toHaveAttribute("href", "/digests/2026-11-17");
    expect(
      screen.getByRole("link", { name: "다음 다이제스트" }),
    ).toHaveAttribute("href", "/digests/2026-11-19");
  });

  it("그 날짜의 다이제스트가 없으면 없다고 알린다", async () => {
    renderDay(problem(404, "없음"));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "그 날짜의 다이제스트가 없습니다.",
    );
  });

  it("날짜 꼴이 아닌 주소는 서버에 묻지 않는다", async () => {
    const { server } = renderDay(day(), "yesterday");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "그 날짜의 다이제스트가 없습니다.",
    );
    expect(server.requests).toHaveLength(0);
  });
});
