import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { Schemas } from "@/features/admin/adminClient";
import { json, problem } from "@/features/admin/adminTestServer";
import { BigEventsPage } from "@/features/admin/BigEventsPage";
import { bigRow } from "@/features/admin/eventTestKit";
import { renderAdminRoutes } from "@/features/admin/reviewTestKit";

const DEFAULT = { minSources: 3, minImportance: 3 };

const STATUS: Schemas["BigEventCriteriaStatusResponse"] = {
  current: DEFAULT,
  changes: [
    {
      previous: null,
      current: DEFAULT,
      actor: "SYSTEM",
      changedAt: "2026-11-05T00:00:00Z",
    },
  ],
};

function renderPage(
  handlers: Record<
    string,
    (request: Request) => Response | Promise<Response>
  > = {},
) {
  let status = STATUS;
  return renderAdminRoutes(
    [{ path: "/admin/big-events", element: <BigEventsPage /> }],
    "/admin/big-events",
    {
      "GET /api/admin/big-events": () =>
        json({
          criteria: status.current,
          events: [
            bigRow(7),
            bigRow(9, { representativeId: null, representative: null }),
          ],
          maxDays: 31,
        }),
      "GET /api/admin/big-event-criteria": () => json(status),
      "POST /api/admin/big-event-criteria-previews": () =>
        json({
          current: DEFAULT,
          proposed: { minSources: 2, minImportance: 3 },
          currentEvents: [bigRow(7), bigRow(9)],
          proposedEvents: [bigRow(7), bigRow(11)],
        }),
      "PUT /api/admin/big-event-criteria": () => {
        status = {
          current: { minSources: 2, minImportance: 3 },
          changes: [
            {
              previous: DEFAULT,
              current: { minSources: 2, minImportance: 3 },
              actor: "OPERATOR",
              changedAt: "2026-11-06T00:00:00Z",
            },
            ...STATUS.changes,
          ],
        };
        return json({
          result: "CHANGED",
          previous: DEFAULT,
          current: { minSources: 2, minImportance: 3 },
        });
      },
      ...handlers,
    },
  ).server;
}

describe("BigEventsPage", () => {
  it("기간의 큰 사건을 지금 기준과 함께 보이고 기준 입력은 지금 값에서 시작한다", async () => {
    renderPage();

    const section = await screen.findByRole("region", {
      name: "기간의 큰 사건",
    });
    expect(section).toHaveTextContent("지금 기준 소스 3 이상 · 중요도 3 이상");
    const rows = within(within(section).getByRole("table")).getAllByRole("row");
    expect(
      within(rows[1]!).getByRole("link", { name: "사건 7" }),
    ).toHaveAttribute("href", "/admin/events/7");
    expect(within(rows[1]!).getAllByRole("cell")[1]).toHaveTextContent(/^4$/);
    expect(within(rows[2]!).getAllByRole("cell")[4]).toHaveTextContent(/^$/);
    expect(await screen.findByLabelText("소스 기준")).toHaveValue("3");
    expect(screen.getByLabelText("중요도 기준")).toHaveValue("3");
    expect(screen.getByRole("region", { name: "기준 이력" })).toHaveTextContent(
      "처음 값",
    );
    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  });

  it("미리보기가 두 목록과 들어오고 빠지는 사건을 보이고, 저장 뒤 기준 · 이력을 다시 읽는다", async () => {
    const server = renderPage();
    const sources = await screen.findByLabelText("소스 기준");
    await waitFor(() => expect(sources).toHaveValue("3"));

    await userEvent.clear(sources);
    await userEvent.type(sources, "2");
    await userEvent.click(screen.getByRole("button", { name: "미리보기" }));

    const preview = await screen.findByRole("region", { name: "미리보기" });
    expect(preview).toHaveTextContent("새로 드는 사건 1건, 빠지는 사건 1건");
    const now = within(
      within(preview).getByRole("table", { name: "지금 기준의 큰 사건" }),
    ).getAllByRole("row");
    expect(within(now[2]!).getAllByRole("cell")[5]).toHaveTextContent(/^빠짐$/);
    const then = within(
      within(preview).getByRole("table", { name: "제안 기준의 큰 사건" }),
    ).getAllByRole("row");
    expect(within(then[1]!).getAllByRole("cell")[5]).toHaveTextContent(/^$/);
    expect(within(then[2]!).getAllByRole("cell")[5]).toHaveTextContent(
      /^새로 듦$/,
    );
    const previewRequest = server.requests.find(
      (r) => r.path === "/api/admin/big-event-criteria-previews",
    );
    expect(JSON.parse(previewRequest!.body)).toMatchObject({
      minSources: 2,
      minImportance: 3,
    });

    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(await screen.findByRole("status")).toHaveTextContent(
      "바꿨다 — 소스 3 이상 · 중요도 3 이상 → 소스 2 이상 · 중요도 3 이상",
    );
    await waitFor(() =>
      expect(
        screen.getByRole("region", { name: "기준 이력" }),
      ).toHaveTextContent("운영자"),
    );
    const put = server.requests.find((r) => r.method === "PUT");
    expect(JSON.parse(put!.body)).toEqual({ minSources: 2, minImportance: 3 });
  });

  it("미리보기 뒤 입력을 바꾸면 다시 미리 봐야 저장할 수 있다", async () => {
    renderPage();
    const sources = await screen.findByLabelText("소스 기준");
    await waitFor(() => expect(sources).toHaveValue("3"));

    await userEvent.click(screen.getByRole("button", { name: "미리보기" }));
    await screen.findByRole("region", { name: "미리보기" });
    expect(screen.getByRole("button", { name: "저장" })).toBeEnabled();
    await userEvent.type(sources, "0");

    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  });

  it("소스 기준이 정수가 아니면 미리 보지 않는다", async () => {
    const server = renderPage();
    const sources = await screen.findByLabelText("소스 기준");
    await waitFor(() => expect(sources).toHaveValue("3"));

    await userEvent.type(sources, ".5");

    expect(screen.getByRole("button", { name: "미리보기" })).toBeDisabled();
    expect(
      screen.getByText("소스 기준에는 정수, 중요도 기준에는 수를 넣는다"),
    ).toBeInTheDocument();
    expect(
      server.requests.filter(
        (r) => r.path === "/api/admin/big-event-criteria-previews",
      ),
    ).toHaveLength(0);
  });

  it("범위 밖 기준의 400 설명을 보인다", async () => {
    renderPage({
      "POST /api/admin/big-event-criteria-previews": () =>
        problem(
          400,
          "큰 사건 기준은 소스 1 이상, 중요도 1 ~ 5 의 소수 2자리까지다",
        ),
    });
    const sources = await screen.findByLabelText("소스 기준");
    await waitFor(() => expect(sources).toHaveValue("3"));

    await userEvent.clear(sources);
    await userEvent.type(sources, "0");
    await userEvent.click(screen.getByRole("button", { name: "미리보기" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "큰 사건 기준은 소스 1 이상, 중요도 1 ~ 5 의 소수 2자리까지다",
    );
    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  });
});
