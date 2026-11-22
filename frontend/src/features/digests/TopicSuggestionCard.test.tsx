import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { TopicSuggestionCard } from "@/features/digests/TopicSuggestionCard";
import type { TopicSuggestions } from "@/features/digests/topicSuggestionQueries";
import type { InterestTags } from "@/features/interests/interestQueries";
import { type Handler, json, problem } from "@/test/fakeServer";
import { bodiesOf, renderRoutes } from "@/test/renderRoutes";

const LEGISLATURE: TopicSuggestions = {
  tagSetId: 7,
  topics: [
    {
      key: "politics.legislature",
      name: "국회·정당·선거",
      categoryName: "정치",
    },
  ],
};

const NONE: TopicSuggestions = { tagSetId: 7, topics: [] };

function tags(tagSetId = 7): InterestTags {
  return {
    tagSet: { id: tagSetId, number: 1, topics: [], regions: [], formats: [] },
    preferences: {
      topics: ["economy"],
      excludedRegions: ["us"],
      excludedFormats: ["brief"],
    },
  };
}

/** 제안 응답을 차례로 내고 마지막 것을 되풀이한다 */
function renderCard(
  suggestions: TopicSuggestions[],
  extra: Record<string, Handler> = {},
  interestTags: InterestTags = tags(),
) {
  const queue = [...suggestions];
  return renderRoutes([{ path: "/", element: <TopicSuggestionCard /> }], "/", {
    "GET /api/topic-suggestions": () =>
      json(queue.length > 1 ? queue.shift() : queue[0]),
    "GET /api/interests/tags": () => json(interestTags),
    ...extra,
  });
}

function suggestionReads(server: ReturnType<typeof renderCard>["server"]) {
  return server.requests.filter((r) => r.path === "/api/topic-suggestions")
    .length;
}

describe("TopicSuggestionCard", () => {
  it("제안할 주제를 대분류 · 중분류 이름과 두 버튼으로 보인다", async () => {
    renderCard([LEGISLATURE]);

    expect(
      await screen.findByRole("heading", { name: "구독해 볼 만한 주제" }),
    ).toBeInTheDocument();
    expect(screen.getByText("정치 · 국회·정당·선거")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "구독" })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "괜찮아요" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "괜찮아요를 누르면 다시 권하지 않습니다. 관심이 더 커지면 다시 권할 수 있습니다.",
      ),
    ).toBeInTheDocument();
    // 주제 관심은 좋아요로 오른다 — 스토리라인 구독은 주제 관심에 들지 않는다
    expect(
      screen.getByText(
        "최근 좋아요를 보면 관심이 있는 주제입니다. 구독하면 매일 다이제스트에 이 주제의 기사가 들어옵니다.",
      ),
    ).toBeInTheDocument();
  });

  it("제안이 없으면 아무것도 그리지 않는다", async () => {
    const { server } = renderCard([NONE]);

    await waitFor(() => expect(suggestionReads(server)).toBe(1));
    expect(screen.queryByRole("heading")).not.toBeInTheDocument();
  });

  it("제안을 읽지 못하면 아무것도 그리지 않는다", async () => {
    const { server } = renderCard([LEGISLATURE], {
      "GET /api/topic-suggestions": () =>
        problem(403, "키가 없다", "urn:yozmnews:problem:llm-key-required"),
    });

    await waitFor(() => expect(suggestionReads(server)).toBe(1));
    expect(screen.queryByRole("heading")).not.toBeInTheDocument();
  });

  it("구독하면 지금 선호에 그 주제를 더해 저장하고 제안을 다시 읽는다", async () => {
    const { server } = renderCard([LEGISLATURE, NONE], {
      "PUT /api/interests/tags": () =>
        json({
          result: "SAVED",
          preferences: {
            topics: ["economy", "politics.legislature"],
            excludedRegions: ["us"],
            excludedFormats: ["brief"],
          },
          activeTagSetId: null,
        }),
    });

    await userEvent.click(await screen.findByRole("button", { name: "구독" }));

    await waitFor(() =>
      expect(
        screen.queryByRole("heading", { name: "구독해 볼 만한 주제" }),
      ).not.toBeInTheDocument(),
    );
    expect(bodiesOf(server, "PUT", "/api/interests/tags")).toEqual([
      {
        tagSetId: 7,
        topics: ["economy", "politics.legislature"],
        excludedRegions: ["us"],
        excludedFormats: ["brief"],
      },
    ]);
  });

  it("괜찮아요를 누르면 제안의 체계 버전과 키로 거절하고 제안을 다시 읽는다", async () => {
    const { server } = renderCard([LEGISLATURE, NONE], {
      "POST /api/interests/declined-suggestions": () =>
        json({
          result: "DECLINED",
          topic: "politics.legislature",
          activeTagSetId: null,
        }),
    });

    await userEvent.click(
      await screen.findByRole("button", { name: "괜찮아요" }),
    );

    await waitFor(() =>
      expect(
        screen.queryByRole("heading", { name: "구독해 볼 만한 주제" }),
      ).not.toBeInTheDocument(),
    );
    expect(
      bodiesOf(server, "POST", "/api/interests/declined-suggestions"),
    ).toEqual([{ tagSetId: 7, topic: "politics.legislature" }]);
  });

  it("제안과 선호의 체계 버전이 다르면 저장하지 않고 둘을 다시 읽는다", async () => {
    const { server } = renderCard([LEGISLATURE], {}, tags(8));

    await userEvent.click(await screen.findByRole("button", { name: "구독" }));

    await waitFor(() => expect(suggestionReads(server)).toBe(2));
    expect(server.requests.filter((r) => r.method === "PUT")).toHaveLength(0);
    expect(
      server.requests.filter((r) => r.path === "/api/interests/tags"),
    ).toHaveLength(2);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("거절할 때 체계 버전이 바뀌었으면 안내 없이 제안과 선호를 다시 읽는다", async () => {
    const { server } = renderCard([LEGISLATURE], {
      "POST /api/interests/declined-suggestions": () =>
        json({ result: "TAG_SET_CHANGED", topic: null, activeTagSetId: 8 }),
    });

    await userEvent.click(
      await screen.findByRole("button", { name: "괜찮아요" }),
    );

    await waitFor(() => expect(suggestionReads(server)).toBe(2));
    await waitFor(() =>
      expect(
        server.requests.filter((r) => r.path === "/api/interests/tags"),
      ).toHaveLength(2),
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("거절을 적었으면 선호는 다시 읽지 않는다", async () => {
    const { server } = renderCard([LEGISLATURE, NONE], {
      "POST /api/interests/declined-suggestions": () =>
        json({
          result: "DECLINED",
          topic: "politics.legislature",
          activeTagSetId: null,
        }),
    });

    await userEvent.click(
      await screen.findByRole("button", { name: "괜찮아요" }),
    );

    await waitFor(() => expect(suggestionReads(server)).toBe(2));
    expect(
      server.requests.filter((r) => r.path === "/api/interests/tags"),
    ).toHaveLength(1);
  });

  it("요청이 실패하면 카드 안에 안내한다", async () => {
    renderCard([LEGISLATURE], {
      "POST /api/interests/declined-suggestions": () =>
        problem(500, "서버 오류"),
    });

    await userEvent.click(
      await screen.findByRole("button", { name: "괜찮아요" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "요청을 처리하지 못했습니다. 잠시 뒤 다시 해 주세요.",
    );
    expect(screen.getByText("정치 · 국회·정당·선거")).toBeInTheDocument();
  });
});
