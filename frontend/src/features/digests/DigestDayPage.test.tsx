import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { DigestDayPage } from "@/features/digests/DigestDayPage";
import type { DigestDay } from "@/features/digests/digestQueries";
import {
  attempt,
  citation,
  day,
  noFeedback,
  shown,
} from "@/features/digests/digestTestKit";
import { json, problem } from "@/test/fakeServer";
import { renderRoutes } from "@/test/renderRoutes";

function renderDay(response: DigestDay | Response, date = "2026-11-18") {
  return renderRoutes(
    [{ path: "/digests/:date", element: <DigestDayPage /> }],
    `/digests/${date}`,
    {
      [`GET /api/digests/${date}`]: () =>
        response instanceof Response ? response : json(response),
      "GET /api/feedback": () => json(noFeedback()),
    },
  );
}

describe("DigestDayPage", () => {
  it("보이는 다이제스트의 요약 · 주요 뉴스 · 추가 헤드라인을 기사 정보와 함께 그린다", async () => {
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
      screen.getByRole("heading", { level: 3, name: "주요 뉴스 제목" }),
    ).toBeInTheDocument();
    expect(screen.getByText("주요 뉴스 요약")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "기사 1" });
    expect(link).toHaveAttribute("href", "https://news.example/1");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link.closest("li")).toHaveTextContent(
      "기사 1 · 한국일보 · 2026. 11. 18. 오전 7:30 GMT+9",
    );
    expect(screen.getByText("한 줄 코멘트")).toBeInTheDocument();
    expect(
      screen.getByText(
        "기간 2026. 11. 17. 오전 7:00 GMT+9 ~ 2026. 11. 18. 오전 7:00 GMT+9",
      ),
    ).toBeInTheDocument();
  });

  it("선정 이유는 구독 주제가 아닌 인용에만 표시하고 범례를 둔다", async () => {
    renderDay(day());

    const subscribed = (
      await screen.findByRole("link", { name: "기사 1" })
    ).closest("li") as HTMLElement;
    const big = screen.getByRole("link", { name: "기사 2" }).closest("li");
    const interest = screen.getByRole("link", { name: "기사 3" }).closest("li");
    expect(within(subscribed).queryByText("구독 주제")).not.toBeInTheDocument();
    expect(big).toHaveTextContent("큰 화제");
    expect(interest).toHaveTextContent("관심 반영");
    // 관심 반영도 구독한 주제의 기사다(ADR-0216) — 범례가 구독 밖으로 읽히지 않게
    expect(
      screen.getByText(/‘관심 반영’은 구독한 주제 가운데/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/표시가 없는 기사는 구독한 주제에서 왔습니다/),
    ).toBeInTheDocument();
  });

  it("구독 주제 인용만 있으면 범례를 두지 않는다", async () => {
    renderDay(
      day({
        shown: shown({
          sections: [
            {
              kind: "MORE_HEADLINES",
              title: null,
              body: null,
              citations: [citation(1)],
            },
          ],
        }),
      }),
    );

    expect(await screen.findByRole("link", { name: "기사 1" })).toBeVisible();
    expect(
      screen.queryByText(/표시가 없는 기사는 구독한 주제에서 왔습니다/),
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
