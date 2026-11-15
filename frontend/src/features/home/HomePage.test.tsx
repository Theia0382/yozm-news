import { act, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { HomePage } from "@/features/home/HomePage";
import type { InterestTags } from "@/features/interests/interestQueries";
import { json } from "@/test/fakeServer";
import { renderRoutes } from "@/test/renderRoutes";

function tags(topics: string[]): InterestTags {
  return {
    tagSet: { id: 1, number: 1, topics: [], regions: [], formats: [] },
    preferences: { topics, excludedRegions: [], excludedFormats: [] },
  };
}

function renderHome(topics: string[]) {
  return renderRoutes([{ path: "/", element: <HomePage /> }], "/", {
    "GET /api/interests/tags": () => json(tags(topics)),
  });
}

describe("HomePage", () => {
  it("구독한 주제가 없으면 관심 설정으로 안내한다", async () => {
    renderHome([]);

    expect(await screen.findByRole("note")).toHaveTextContent(
      "구독한 주제가 없어 다이제스트에 구독 기사가 들어가지 않습니다. 관심 있는 주제를 고르세요. 관심 설정으로 가기",
    );
    expect(
      screen.getByRole("link", { name: "관심 설정으로 가기" }),
    ).toHaveAttribute("href", "/interests");
  });

  it("구독한 주제가 있으면 안내하지 않는다", async () => {
    const { server } = renderHome(["politics"]);

    expect(
      await screen.findByRole("heading", { level: 1, name: "요즘뉴스" }),
    ).toBeInTheDocument();
    await expect.poll(() => server.requests.length).toBeGreaterThan(0);
    // 응답을 그릴 때까지 기다린다 — TanStack Query 는 결과 알림을 다음 타이머로 미룬다
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)));
    expect(screen.queryByRole("note")).not.toBeInTheDocument();
  });
});
