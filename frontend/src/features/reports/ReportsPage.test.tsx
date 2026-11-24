import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ReportsPage } from "@/features/reports/ReportsPage";
import { summary } from "@/features/reports/reportTestKit";
import { json } from "@/test/fakeServer";
import { renderRoutes } from "@/test/renderRoutes";

const ROUTES = [{ path: "/reports", element: <ReportsPage /> }];

describe("ReportsPage", () => {
  it("처음에는 주간을 최근 순으로 보이고, 줄마다 기간과 상태(내용 있음은 적지 않음)를 단다", async () => {
    const { server } = renderRoutes(ROUTES, "/reports", {
      "GET /api/reports": () =>
        json({
          reports: [
            summary(3, {
              firstDay: "2026-11-23",
              lastDay: "2026-11-29",
              status: "RUNNING",
              completedAt: null,
            }),
            summary(2),
            summary(1, {
              firstDay: "2026-11-09",
              lastDay: "2026-11-15",
              status: "FAILED",
              failure: "QUOTA_EXHAUSTED",
            }),
          ],
          next: null,
        }),
    });

    const list = await screen.findByRole("list", { name: "주간 리포트" });
    expect(server.requests.map((r) => r.search)).toEqual(["?kind=WEEK"]);
    expect(screen.getByRole("button", { name: "주간" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    const items = within(list).getAllByRole("listitem");
    expect(items.map((item) => item.textContent)).toEqual([
      "2026. 11. 23. ~ 2026. 11. 29. · 만드는 중",
      "2026. 11. 16. ~ 2026. 11. 22.",
      "2026. 11. 09. ~ 2026. 11. 15. · 만들지 못함",
    ]);
    expect(
      within(items[1]!).getByRole("link", {
        name: "2026. 11. 16. ~ 2026. 11. 22.",
      }),
    ).toHaveAttribute("href", "/reports/2");
    expect(screen.queryByRole("button", { name: "더 보기" })).toBeNull();
  });

  it("월간 탭을 누르면 월간을 묻고 주소에 남긴다", async () => {
    const { server, router } = renderRoutes(ROUTES, "/reports", {
      "GET /api/reports": (request) =>
        json({
          reports:
            new URL(request.url).searchParams.get("kind") === "MONTH"
              ? [
                  summary(9, {
                    kind: "MONTH",
                    firstDay: "2026-10-01",
                    lastDay: "2026-10-31",
                  }),
                ]
              : [summary(2)],
          next: null,
        }),
    });
    const user = userEvent.setup();
    await screen.findByRole("list", { name: "주간 리포트" });

    await user.click(screen.getByRole("button", { name: "월간" }));

    const list = await screen.findByRole("list", { name: "월간 리포트" });
    expect(within(list).getByRole("link")).toHaveTextContent(
      "2026. 10. 01. ~ 2026. 10. 31.",
    );
    expect(router.state.location.search).toBe("?kind=MONTH");
    expect(server.requests.map((r) => r.search)).toEqual([
      "?kind=WEEK",
      "?kind=MONTH",
    ]);
  });

  it("더 보기는 앞 응답의 next 로 다음 쪽을 묻는다", async () => {
    const { server } = renderRoutes(ROUTES, "/reports?kind=WEEK", {
      "GET /api/reports": (request) =>
        new URL(request.url).searchParams.get("before") === "2"
          ? json({ reports: [summary(1)], next: null })
          : json({ reports: [summary(3), summary(2)], next: 2 }),
    });
    const user = userEvent.setup();
    const list = await screen.findByRole("list", { name: "주간 리포트" });

    await user.click(screen.getByRole("button", { name: "더 보기" }));

    expect(await within(list).findAllByRole("listitem")).toHaveLength(3);
    expect(server.requests.map((r) => r.search)).toEqual([
      "?kind=WEEK",
      "?kind=WEEK&before=2",
    ]);
  });

  it("리포트가 없으면 어떻게 생기는지 안내한다", async () => {
    renderRoutes(ROUTES, "/reports", {
      "GET /api/reports": () => json({ reports: [], next: null }),
    });

    expect(
      await screen.findByText(/관심 설정에서 주간 · 월간 변화 리포트를 켜면/),
    ).toBeInTheDocument();
  });
});
