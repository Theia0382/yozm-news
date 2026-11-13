import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { Schemas } from "@/features/admin/adminClient";
import { json, problem } from "@/test/fakeServer";
import { BigStorylinesPage } from "@/features/admin/BigStorylinesPage";
import { bigRow } from "@/features/admin/eventTestKit";
import { renderAdminRoutes } from "@/features/admin/reviewTestKit";

const DEFAULT = { minIntensity: 15 };

const STATUS: Schemas["BigStorylineCriteriaStatusResponse"] = {
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
    [{ path: "/admin/big-storylines", element: <BigStorylinesPage /> }],
    "/admin/big-storylines",
    {
      "GET /api/admin/big-storylines": () =>
        json({
          criteria: status.current,
          storylines: [
            bigRow(7),
            bigRow(9, {
              state: "ENDED",
              importance: null,
              representativeArticleId: null,
              representative: null,
            }),
          ],
          unlinked: { events: 2, articles: 5 },
          maxDays: 31,
        }),
      "GET /api/admin/big-storyline-criteria": () => json(status),
      "POST /api/admin/big-storyline-criteria-previews": () =>
        json({
          current: DEFAULT,
          proposed: { minIntensity: 20 },
          currentStorylines: [bigRow(7), bigRow(9)],
          proposedStorylines: [bigRow(7), bigRow(11)],
        }),
      "PUT /api/admin/big-storyline-criteria": () => {
        status = {
          current: { minIntensity: 20 },
          changes: [
            {
              previous: DEFAULT,
              current: { minIntensity: 20 },
              actor: "OPERATOR",
              changedAt: "2026-11-06T00:00:00Z",
            },
            ...STATUS.changes,
          ],
        };
        return json({
          result: "CHANGED",
          previous: DEFAULT,
          current: { minIntensity: 20 },
        });
      },
      ...handlers,
    },
  ).server;
}

describe("BigStorylinesPage", () => {
  it("기간의 큰 스토리라인을 지금 기준 · 소속 없는 사건 수와 함께 보이고 기준 입력은 지금 값에서 시작한다", async () => {
    renderPage();

    const section = await screen.findByRole("region", {
      name: "기간의 큰 스토리라인",
    });
    expect(section).toHaveTextContent("지금 기준 보도 강도 15.00 이상");
    expect(section).toHaveTextContent(
      "스토리라인에 들지 않은 사건 2건 · 기사 5건",
    );
    const rows = within(within(section).getByRole("table")).getAllByRole("row");
    expect(
      within(rows[0]!)
        .getAllByRole("columnheader")
        .map((header) => header.textContent),
    ).toEqual([
      "스토리라인",
      "상태",
      "사건 수",
      "보도 강도(기간)",
      "소스 수(기간)",
      "중요도 평균(기간)",
      "기사 수(기간 / 전체)",
      "대표 기사",
    ]);
    const first = within(rows[1]!).getAllByRole("cell");
    expect(first[0]).toHaveTextContent(/^스토리라인 7$/);
    expect(
      within(first[0]!).getByRole("link", { name: "스토리라인 7" }),
    ).toHaveAttribute("href", "/admin/storylines/7");
    expect(first[1]).toHaveTextContent(/^진행$/);
    expect(first[2]).toHaveTextContent(/^2$/);
    expect(first[3]).toHaveTextContent(/^17\.50$/);
    expect(first[4]).toHaveTextContent(/^4$/);
    expect(first[5]).toHaveTextContent(/^3\.75$/);
    expect(first[6]).toHaveTextContent(/^3 \/ 12$/);
    expect(first[7]).toHaveTextContent("국회 본회의 통과");
    const second = within(rows[2]!).getAllByRole("cell");
    expect(second[1]).toHaveTextContent(/^끝남$/);
    expect(second[5]).toHaveTextContent(/^없음$/);
    expect(second[7]).toHaveTextContent(/^$/);
    expect(await screen.findByLabelText("보도 강도 기준")).toHaveValue("15.00");
    // 기준 입력은 하나다
    expect(
      within(screen.getByRole("region", { name: "기준 바꾸기" })).getAllByRole(
        "textbox",
      ),
    ).toHaveLength(1);
    expect(
      screen.getByRole("region", { name: "기준 바꾸기" }),
    ).toHaveTextContent(
      "그 기간에 기사가 있는 소스 수와 기간의 일수로 나눈 값이다",
    );
    expect(screen.getByRole("region", { name: "기준 이력" })).toHaveTextContent(
      "처음 값",
    );
    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  });

  it("미리보기가 두 목록과 들어오고 빠지는 스토리라인을 보이고, 저장 뒤 기준 · 이력을 다시 읽는다", async () => {
    const server = renderPage();
    const intensity = await screen.findByLabelText("보도 강도 기준");
    await waitFor(() => expect(intensity).toHaveValue("15.00"));

    await userEvent.clear(intensity);
    await userEvent.type(intensity, "20");
    await userEvent.click(screen.getByRole("button", { name: "미리보기" }));

    const preview = await screen.findByRole("region", { name: "미리보기" });
    expect(preview).toHaveTextContent(
      "새로 드는 스토리라인 1건, 빠지는 스토리라인 1건",
    );
    const now = within(
      within(preview).getByRole("table", { name: "지금 기준의 큰 스토리라인" }),
    ).getAllByRole("row");
    expect(within(now[2]!).getAllByRole("cell")[8]).toHaveTextContent(/^빠짐$/);
    const then = within(
      within(preview).getByRole("table", { name: "제안 기준의 큰 스토리라인" }),
    ).getAllByRole("row");
    expect(within(then[1]!).getAllByRole("cell")[8]).toHaveTextContent(/^$/);
    expect(within(then[2]!).getAllByRole("cell")[8]).toHaveTextContent(
      /^새로 듦$/,
    );
    const previewRequest = server.requests.find(
      (r) => r.path === "/api/admin/big-storyline-criteria-previews",
    );
    expect(JSON.parse(previewRequest!.body)).toMatchObject({
      minIntensity: 20,
    });

    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(await screen.findByRole("status")).toHaveTextContent(
      "바꿨다 — 보도 강도 15.00 이상 → 보도 강도 20.00 이상",
    );
    await waitFor(() =>
      expect(
        screen.getByRole("region", { name: "기준 이력" }),
      ).toHaveTextContent("운영자"),
    );
    const put = server.requests.find((r) => r.method === "PUT");
    expect(JSON.parse(put!.body)).toEqual({ minIntensity: 20 });
  });

  it("미리보기 뒤 입력을 바꾸면 다시 미리 봐야 저장할 수 있다", async () => {
    renderPage();
    const intensity = await screen.findByLabelText("보도 강도 기준");
    await waitFor(() => expect(intensity).toHaveValue("15.00"));

    await userEvent.click(screen.getByRole("button", { name: "미리보기" }));
    await screen.findByRole("region", { name: "미리보기" });
    expect(screen.getByRole("button", { name: "저장" })).toBeEnabled();
    await userEvent.type(intensity, "0");

    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  });

  it("보도 강도 기준이 수가 아니면 미리 보지 않는다", async () => {
    const server = renderPage();
    const intensity = await screen.findByLabelText("보도 강도 기준");
    await waitFor(() => expect(intensity).toHaveValue("15.00"));

    await userEvent.type(intensity, "x");

    expect(screen.getByRole("button", { name: "미리보기" })).toBeDisabled();
    expect(
      screen.getByText("보도 강도 기준에는 수를 넣는다"),
    ).toBeInTheDocument();
    expect(
      server.requests.filter(
        (r) => r.path === "/api/admin/big-storyline-criteria-previews",
      ),
    ).toHaveLength(0);
  });

  it("범위 밖 기준의 400 설명을 보인다", async () => {
    renderPage({
      "POST /api/admin/big-storyline-criteria-previews": () =>
        problem(
          400,
          "큰 판정 기준은 0 보다 크고 9999.99 이하인 소수 2자리까지다",
        ),
    });
    const intensity = await screen.findByLabelText("보도 강도 기준");
    await waitFor(() => expect(intensity).toHaveValue("15.00"));

    await userEvent.clear(intensity);
    await userEvent.type(intensity, "0");
    await userEvent.click(screen.getByRole("button", { name: "미리보기" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "큰 판정 기준은 0 보다 크고 9999.99 이하인 소수 2자리까지다",
    );
    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  });
});
