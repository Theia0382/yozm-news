import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { describe, expect, it } from "vitest";
import { createAdminClient, type Schemas } from "@/features/admin/adminClient";
import { AdminSessionContext } from "@/features/admin/adminSession";
import {
  fakeServer,
  type Handler,
  json,
  problem,
  TEST_BASE_URL,
} from "@/test/fakeServer";
import { EventPublicationsPage } from "@/features/admin/EventPublicationsPage";

type Listing = Schemas["StrandedPublicationListResponse"];

const EXHAUSTED_ID = "10000000-0000-0000-0000-000000000001";
const UNREADABLE_ID = "20000000-0000-0000-0000-000000000002";

const LISTING: Listing = {
  total: 2,
  limit: 500,
  publications: [
    {
      id: EXHAUSTED_ID,
      reason: "EXHAUSTED",
      eventType: "net.yozmnews.source.ArticleFetched",
      listenerId:
        "net.yozmnews.article.application.ArticleFetchedListener.on(net.yozmnews.source.ArticleFetched)",
      publishedAt: "2026-10-30T01:00:00Z",
      completionAttempts: 6,
      lastResubmittedAt: "2026-10-30T01:25:00Z",
    },
    {
      id: UNREADABLE_ID,
      reason: "UNREADABLE",
      eventType: "net.yozmnews.source.RenamedEvent",
      listenerId: "listener",
      publishedAt: "2026-10-31T02:00:00Z",
      completionAttempts: 1,
      lastResubmittedAt: null,
    },
  ],
};

const EMPTY: Listing = { total: 0, limit: 500, publications: [] };

function renderPage(
  handlers: Record<string, Handler> = {},
): ReturnType<typeof fakeServer> & { queryClient: QueryClient } {
  const server = fakeServer({
    "GET /api/admin/event-publications": () => json(LISTING),
    ...handlers,
  });
  const client = createAdminClient({
    baseUrl: TEST_BASE_URL,
    fetch: server.fetch,
    token: () => "token",
  });
  const router = createMemoryRouter([
    { path: "/", element: <EventPublicationsPage /> },
  ]);
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <AdminSessionContext.Provider value={client}>
        <RouterProvider router={router} />
      </AdminSessionContext.Provider>
    </QueryClientProvider>,
  );
  return Object.assign(server, { queryClient });
}

function requestsTo(server: ReturnType<typeof fakeServer>, key: string) {
  return server.requests.filter(
    (request) => `${request.method} ${request.path}` === key,
  );
}

describe("EventPublicationsPage", () => {
  it("남겨진 발행마다 까닭 · 이벤트 종류 · 리스너 · 발행 시각 · 처리 시도 · 마지막 재제출을 보인다", async () => {
    renderPage();

    const exhausted = (
      await screen.findByText("net.yozmnews.source.ArticleFetched")
    ).closest("tr");
    expect(exhausted).not.toBeNull();
    const row = within(exhausted as HTMLElement);
    expect(row.getByText("처리 시도 상한에 이름")).toBeInTheDocument();
    expect(
      row.getByText(
        "net.yozmnews.article.application.ArticleFetchedListener.on(net.yozmnews.source.ArticleFetched)",
      ),
    ).toBeInTheDocument();
    expect(row.getByText("2026. 10. 30. 오전 10:00 GMT+9")).toBeInTheDocument();
    expect(row.getByText("6")).toBeInTheDocument();
    expect(row.getByText("2026. 10. 30. 오전 10:25 GMT+9")).toBeInTheDocument();

    const unreadable = screen
      .getByText("net.yozmnews.source.RenamedEvent")
      .closest("tr");
    const other = within(unreadable as HTMLElement);
    expect(
      other.getByText("이벤트를 읽을 수 없음 — 폐기만 가능"),
    ).toBeInTheDocument();
    expect(other.getByText("없음")).toBeInTheDocument();
  });

  it("남겨진 발행이 없으면 없다고 알리고, 고른 발행이 없으면 조작할 수 없다", async () => {
    renderPage({ "GET /api/admin/event-publications": () => json(EMPTY) });

    expect(
      await screen.findByText("남겨진 발행이 없습니다."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 처리" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "폐기" })).toBeDisabled();
  });

  it("서버가 준 상한보다 많으면 앞쪽만 보인다고 알린다", async () => {
    renderPage({
      "GET /api/admin/event-publications": () =>
        json({ ...LISTING, total: 9, limit: 2 }),
    });

    expect(
      await screen.findByText(/남겨진 발행 9건 가운데 오래된 2건만 보입니다/),
    ).toBeInTheDocument();
  });

  it("모두 골라 다시 처리하면 고른 ID 를 보내고 결과를 판별자마다 세어 알린 뒤 목록을 다시 읽는다", async () => {
    const user = userEvent.setup();
    const server = renderPage({
      "POST /api/admin/event-publications/resubmission": () =>
        json({
          results: [
            { id: EXHAUSTED_ID, result: "RESUBMITTED" },
            { id: UNREADABLE_ID, result: "UNREADABLE" },
          ],
        }),
    });

    await user.click(
      await screen.findByRole("checkbox", { name: "모두 고르기" }),
    );
    expect(screen.getByText("2건 고름")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "다시 처리" }));

    expect(await screen.findByRole("status")).toHaveTextContent(
      "다시 처리 결과: 다시 처리함 1건 · 이벤트를 읽을 수 없어 다시 처리하지 못함 — 폐기만 가능 1건 — 처리는 뒤에서 이어지므로 잠시 뒤 새로고침해 확인합니다",
    );
    const posted = requestsTo(
      server,
      "POST /api/admin/event-publications/resubmission",
    );
    expect(posted).toHaveLength(1);
    expect(JSON.parse(posted[0]?.body ?? "")).toEqual({
      ids: [EXHAUSTED_ID, UNREADABLE_ID],
    });
    expect(
      requestsTo(server, "GET /api/admin/event-publications"),
    ).toHaveLength(2);
    expect(screen.getByText("0건 고름")).toBeInTheDocument();
  });

  it("한 발행만 고르면 그 ID 만 보낸다", async () => {
    const user = userEvent.setup();
    const server = renderPage({
      "POST /api/admin/event-publications/resubmission": () =>
        json({ results: [{ id: UNREADABLE_ID, result: "NOT_FOUND" }] }),
    });

    await user.click(
      await screen.findByRole("checkbox", {
        name: `발행 ${UNREADABLE_ID} 고르기`,
      }),
    );
    await user.click(screen.getByRole("button", { name: "다시 처리" }));

    expect(await screen.findByRole("status")).toHaveTextContent(
      "다시 처리 결과: 이미 없음(완료됐거나 폐기됨) 1건",
    );
    const posted = requestsTo(
      server,
      "POST /api/admin/event-publications/resubmission",
    );
    expect(JSON.parse(posted[0]?.body ?? "")).toEqual({ ids: [UNREADABLE_ID] });
  });

  it("폐기는 확인 대화상자를 거친다 — 취소하면 보내지 않고, 확인하면 보내고 결과를 알린다", async () => {
    const user = userEvent.setup();
    const server = renderPage({
      "POST /api/admin/event-publications/discard": () =>
        json({
          results: [
            { id: EXHAUSTED_ID, result: "DISCARDED" },
            { id: UNREADABLE_ID, result: "CHANGED" },
          ],
        }),
    });

    await user.click(
      await screen.findByRole("checkbox", { name: "모두 고르기" }),
    );
    await user.click(screen.getByRole("button", { name: "폐기" }));
    const dialog = await screen.findByRole("alertdialog");
    expect(dialog).toHaveTextContent("발행 2건을 폐기할까요?");
    expect(dialog).toHaveTextContent("되돌릴 수 없고");
    await user.click(within(dialog).getByRole("button", { name: "취소" }));
    expect(
      requestsTo(server, "POST /api/admin/event-publications/discard"),
    ).toHaveLength(0);

    await user.click(screen.getByRole("button", { name: "폐기" }));
    await user.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", {
        name: "폐기",
      }),
    );

    expect(await screen.findByRole("status")).toHaveTextContent(
      "폐기 결과: 폐기함 1건 · 그사이 바뀌어 지우지 않음 1건",
    );
    const posted = requestsTo(
      server,
      "POST /api/admin/event-publications/discard",
    );
    expect(posted).toHaveLength(1);
    expect(JSON.parse(posted[0]?.body ?? "")).toEqual({
      ids: [EXHAUSTED_ID, UNREADABLE_ID],
    });
    expect(
      requestsTo(server, "GET /api/admin/event-publications"),
    ).toHaveLength(2);
  });

  it("폐기 대화상자는 연 때의 대상을 지킨다 — 열린 동안 목록이 바뀌어도 제목과 보내는 ID 가 그대로다", async () => {
    const user = userEvent.setup();
    const server = renderPage({
      "POST /api/admin/event-publications/discard": () =>
        json({
          results: [
            { id: EXHAUSTED_ID, result: "DISCARDED" },
            { id: UNREADABLE_ID, result: "NOT_FOUND" },
          ],
        }),
    });

    await user.click(
      await screen.findByRole("checkbox", { name: "모두 고르기" }),
    );
    await user.click(screen.getByRole("button", { name: "폐기" }));
    expect(await screen.findByRole("alertdialog")).toHaveTextContent(
      "발행 2건을 폐기할까요?",
    );

    // 대화상자가 열린 동안 한 건이 사라진 목록으로 다시 읽는다
    server.handlers["GET /api/admin/event-publications"] = () =>
      json({ ...LISTING, total: 1, publications: [LISTING.publications[0]] });
    await server.queryClient.invalidateQueries();
    await waitFor(() =>
      expect(
        requestsTo(server, "GET /api/admin/event-publications"),
      ).toHaveLength(2),
    );
    await waitFor(() =>
      expect(screen.queryByText("net.yozmnews.source.RenamedEvent")).toBeNull(),
    );

    const dialog = screen.getByRole("alertdialog");
    expect(dialog).toHaveTextContent("발행 2건을 폐기할까요?");
    await user.click(within(dialog).getByRole("button", { name: "폐기" }));

    expect(await screen.findByRole("status")).toHaveTextContent(
      "폐기 결과: 폐기함 1건 · 이미 없음(완료됐거나 폐기됨) 1건",
    );
    const posted = requestsTo(
      server,
      "POST /api/admin/event-publications/discard",
    );
    expect(JSON.parse(posted[0]?.body ?? "")).toEqual({
      ids: [EXHAUSTED_ID, UNREADABLE_ID],
    });
  });

  it("조작이 거절되면 서버의 설명을 알리고, 다음 조작이 성공하면 그 거절을 지운다", async () => {
    const user = userEvent.setup();
    renderPage({
      "POST /api/admin/event-publications/resubmission": () =>
        problem(400, "한 번에 500건까지 처리한다"),
      "POST /api/admin/event-publications/discard": () =>
        json({ results: [{ id: EXHAUSTED_ID, result: "DISCARDED" }] }),
    });

    await user.click(
      await screen.findByRole("checkbox", { name: "모두 고르기" }),
    );
    await user.click(screen.getByRole("button", { name: "다시 처리" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "한 번에 500건까지 처리한다",
    );

    await user.click(screen.getByRole("button", { name: "폐기" }));
    await user.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", {
        name: "폐기",
      }),
    );
    expect(await screen.findByRole("status")).toHaveTextContent(
      "폐기 결과: 폐기함 1건",
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("목록을 읽지 못하면 그 이유를 알린다", async () => {
    renderPage({
      "GET /api/admin/event-publications": () => problem(500, "서버 오류"),
    });

    expect(await screen.findByRole("alert")).toHaveTextContent("서버 오류");
  });
});
