import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { DigestDatesPage } from "@/features/digests/DigestDatesPage";
import type { DigestDates } from "@/features/digests/digestQueries";
import { json } from "@/test/fakeServer";
import { renderRoutes } from "@/test/renderRoutes";

const FIRST: DigestDates = {
  dates: [
    { date: "2026-11-18", state: "CONTENT" },
    { date: "2026-11-17", state: "FAILED" },
  ],
  next: "2026-11-17",
};

const SECOND: DigestDates = {
  dates: [
    { date: "2026-11-16", state: "EMPTY" },
    { date: "2026-11-15", state: "IN_PROGRESS" },
  ],
  next: null,
};

function renderDates(pages: Record<string, DigestDates>) {
  return renderRoutes(
    [{ path: "/digests", element: <DigestDatesPage /> }],
    "/digests",
    {
      "GET /api/digests": (request) => {
        const before = new URL(request.url).searchParams.get("before") ?? "";
        return json(pages[before]);
      },
    },
  );
}

describe("DigestDatesPage", () => {
  it("다이제스트가 있는 날짜를 늦은 순으로 보이고 내용이 없는 날짜에만 상태를 붙이며 더 보기로 다음 쪽을 잇는다", async () => {
    const user = userEvent.setup();
    const { server } = renderDates({ "": FIRST, "2026-11-17": SECOND });

    const first = await screen.findByRole("link", {
      name: "2026. 11. 18. 다이제스트",
    });
    expect(first).toHaveAttribute("href", "/digests/2026-11-18");
    // 내용이 있는 날짜는 링크 글자가 곧 상태라 상태 이름을 붙이지 않는다
    expect(first.closest("li")).toHaveTextContent(
      /^2026\. 11\. 18\. 다이제스트$/,
    );
    expect(
      screen
        .getByRole("link", { name: "2026. 11. 17. 다이제스트" })
        .closest("li"),
    ).toHaveTextContent("만들지 못함");

    await user.click(screen.getByRole("button", { name: "더 보기" }));

    const list = await screen.findByRole("list");
    expect(
      await within(list).findByRole("link", {
        name: "2026. 11. 16. 다이제스트",
      }),
    ).toBeInTheDocument();
    const items = within(list).getAllByRole("listitem");
    expect(items.map((item) => item.textContent)).toEqual([
      "2026. 11. 18. 다이제스트",
      "2026. 11. 17. 다이제스트 · 만들지 못함",
      "2026. 11. 16. 다이제스트 · 넣을 기사 없음",
      "2026. 11. 15. 다이제스트 · 만드는 중",
    ]);
    expect(server.requests.map((r) => r.search)).toEqual([
      "",
      "?before=2026-11-17",
    ]);
    expect(
      screen.queryByRole("button", { name: "더 보기" }),
    ).not.toBeInTheDocument();
  });

  it("날짜가 없으면 아직 없다고 알린다", async () => {
    renderDates({ "": { dates: [], next: null } });

    expect(
      await screen.findByText("아직 만든 다이제스트가 없습니다."),
    ).toBeInTheDocument();
  });
});
