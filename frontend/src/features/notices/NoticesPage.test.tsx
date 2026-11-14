import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { describe, expect, it } from "vitest";
import type { Notice, NoticePage } from "@/features/notices/noticeQueries";
import { NoticesPage } from "@/features/notices/NoticesPage";
import { RequireSignIn } from "@/features/session/RequireSignIn";
import { ApiClientContext, createApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";
import {
  fakeServer,
  type Handler,
  json,
  noContent,
  problem,
  TEST_BASE_URL,
} from "@/test/fakeServer";

type Me = components["schemas"]["MeResponse"];

const SIGNED_IN: Me = {
  result: "SIGNED_IN",
  userId: 7,
  email: "admin@example.com",
  displayName: "운영자",
  role: "ADMIN",
  passwordChangeRequired: false,
  llmKeyRequired: false,
};

const ANONYMOUS: Me = {
  result: "ANONYMOUS",
  userId: null,
  email: null,
  displayName: null,
  role: null,
  passwordChangeRequired: false,
  llmKeyRequired: false,
};

/** 안 읽은 피드 상태 알림 — 피드 셋(지금 계속 실패 · 정상 · 오래 새 기사 없음) */
const GATHERED: Notice = {
  id: 12,
  kind: "FEED_STATUS",
  revision: 3,
  createdAt: "2026-11-13T00:00:00Z",
  updatedAt: "2026-11-13T02:00:00Z",
  read: false,
  feedStatus: {
    feeds: [
      {
        feedId: 11,
        sourceName: "알림일보",
        section: "정치",
        firstFrom: "ACTIVE",
        lastTo: "ACTIVE",
        firstAt: "2026-11-13T00:00:00Z",
        lastAt: "2026-11-13T01:00:00Z",
        changes: 2,
      },
      {
        feedId: 12,
        sourceName: "알림일보",
        section: "경제",
        firstFrom: "ACTIVE",
        lastTo: "DEGRADED",
        firstAt: "2026-11-13T02:00:00Z",
        lastAt: "2026-11-13T02:00:00Z",
        changes: 1,
      },
      {
        feedId: 13,
        sourceName: null,
        section: null,
        firstFrom: "ACTIVE",
        lastTo: "STALE",
        firstAt: "2026-11-13T01:00:00Z",
        lastAt: "2026-11-13T01:00:00Z",
        changes: 1,
      },
    ],
  },
  passwordChanged: null,
  keyInvalidated: null,
};

/** 읽은 피드 상태 알림 하나 */
const OLD: Notice = {
  id: 5,
  kind: "FEED_STATUS",
  revision: 1,
  createdAt: "2026-11-12T00:00:00Z",
  updatedAt: "2026-11-12T00:00:00Z",
  read: true,
  feedStatus: {
    feeds: [
      {
        feedId: 11,
        sourceName: "알림일보",
        section: "정치",
        firstFrom: "ACTIVE",
        lastTo: "DEGRADED",
        firstAt: "2026-11-12T00:00:00Z",
        lastAt: "2026-11-12T00:00:00Z",
        changes: 1,
      },
    ],
  },
  passwordChanged: null,
  keyInvalidated: null,
};

/** 복구 코드로 재설정한 비밀번호 알림 */
const PASSWORD: Notice = {
  id: 20,
  kind: "PASSWORD_CHANGED",
  revision: 1,
  createdAt: "2026-11-13T03:00:00Z",
  updatedAt: "2026-11-13T03:00:00Z",
  read: false,
  feedStatus: null,
  passwordChanged: { method: "RECOVERY", changedAt: "2026-11-13T03:00:00Z" },
  keyInvalidated: null,
};

/** 공급자가 받아 주지 않아 무효가 된 키의 알림 */
const KEY_INVALIDATED: Notice = {
  id: 30,
  kind: "KEY_INVALIDATED",
  revision: 1,
  createdAt: "2026-11-13T03:00:05Z",
  updatedAt: "2026-11-13T03:00:05Z",
  read: false,
  feedStatus: null,
  passwordChanged: null,
  keyInvalidated: {
    provider: "ANTHROPIC",
    invalidatedAt: "2026-11-13T03:00:00Z",
  },
};

function page(notices: Notice[], nextBefore: number | null = null): NoticePage {
  return { notices, nextBefore };
}

function renderNotices(
  notices: Handler,
  read: Handler = () => noContent(),
  unread: Handler = () => json({ count: 0 }),
) {
  let signedIn = true;
  const server = fakeServer({
    "GET /api/me": () => json(signedIn ? SIGNED_IN : ANONYMOUS),
    "GET /api/notices/unread-count": unread,
    "GET /api/notices": notices,
    "POST /api/notices/read": read,
  });
  const router = createMemoryRouter(
    [
      {
        element: <RequireSignIn />,
        children: [
          { path: "/", element: <p>첫 화면</p> },
          { path: "/notices", element: <NoticesPage /> },
        ],
      },
      { path: "/login", element: <p>로그인 화면</p> },
    ],
    { initialEntries: ["/notices"] },
  );
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <ApiClientContext.Provider
        value={createApiClient({ baseUrl: TEST_BASE_URL, fetch: server.fetch })}
      >
        <RouterProvider router={router} />
      </ApiClientContext.Provider>
    </QueryClientProvider>,
  );
  return {
    server,
    router,
    signOut: () => {
      signedIn = false;
    },
    reads: () =>
      server.requests
        .filter((r) => r.method === "POST" && r.path === "/api/notices/read")
        .map((r) => JSON.parse(r.body) as unknown),
  };
}

describe("NoticesPage", () => {
  it("피드 상태 알림을 지금 상태로 묶어 보이고 안 읽은 알림을 강조한다", async () => {
    renderNotices(() => json(page([GATHERED, OLD])));

    const items = await screen.findAllByRole("article");
    expect(items).toHaveLength(2);
    const gathered = items[0]!;
    expect(
      within(gathered).getByRole("heading", { level: 2 }),
    ).toHaveTextContent("새 알림피드 상태가 바뀌었습니다");
    expect(
      within(items[1]!).getByRole("heading", { level: 2 }),
    ).toHaveTextContent(/^피드 상태가 바뀌었습니다$/);
    expect(
      within(gathered)
        .getAllByRole("heading", { level: 3 })
        .map((h) => h.textContent),
    ).toEqual([
      "지금 계속 실패 — 1개",
      "지금 오래 새 기사 없음 — 1개",
      "지금 정상 — 1개",
    ]);
    expect(
      within(gathered)
        .getAllByRole("listitem")
        .map((li) => li.textContent),
    ).toEqual([
      "알림일보 · 경제 정상 → 계속 실패 · 2026. 11. 13. 오전 11:00 GMT+9",
      "이름 없는 피드 (ID 13) 정상 → 오래 새 기사 없음 · 2026. 11. 13. 오전 10:00 GMT+9",
      "알림일보 · 정치 정상 → 정상 · 2번 바뀜 · 2026. 11. 13. 오전 10:00 GMT+9",
    ]);
    expect(
      within(gathered).getByRole("link", {
        name: "운영자 화면에서 자세히 보기",
      }),
    ).toHaveAttribute("href", "/admin");
  });

  it("비밀번호 알림은 바꾼 방법과 시각을 보인다", async () => {
    renderNotices(() => json(page([PASSWORD])));

    const item = await screen.findByRole("article");
    expect(within(item).getByRole("heading", { level: 2 })).toHaveTextContent(
      "새 알림비밀번호가 바뀌었습니다",
    );
    expect(item).toHaveTextContent(
      "복구 코드로 재설정했습니다. 2026. 11. 13. 오후 12:00 GMT+9",
    );
    expect(item).toHaveTextContent(
      "직접 바꾸지 않았다면 바로 비밀번호를 바꾸세요.",
    );
  });

  it("키 무효 알림은 공급자와 시각을 보이고 키 화면으로 잇는다", async () => {
    renderNotices(() => json(page([KEY_INVALIDATED])));

    const item = await screen.findByRole("article");
    expect(within(item).getByRole("heading", { level: 2 })).toHaveTextContent(
      "새 알림LLM 키가 무효가 됐습니다",
    );
    expect(item).toHaveTextContent(
      "Anthropic 키를 공급자가 받아 주지 않았습니다. 2026. 11. 13. 오후 12:00 GMT+9",
    );
    expect(item).toHaveTextContent(
      "새 키를 등록할 때까지 이 키로는 다이제스트를 만들지 못합니다.",
    );
    expect(
      within(item).getByRole("link", { name: "LLM 키 화면에서 확인하기" }),
    ).toHaveAttribute("href", "/keys");
  });

  it("받은 쪽의 안 읽은 알림만 받은 판까지 읽음으로 보낸다", async () => {
    const { reads } = renderNotices(() => json(page([GATHERED, OLD])));

    await waitFor(() =>
      expect(reads()).toEqual([{ notices: [{ id: 12, revision: 3 }] }]),
    );
    expect(screen.getByText("새 알림")).toBeInTheDocument();
  });

  it("읽음을 보낸 뒤 머리줄의 안 읽은 수를 다시 읽는다", async () => {
    let marked = false;
    const { server } = renderNotices(
      () => json(page([GATHERED])),
      () => {
        marked = true;
        return noContent();
      },
      () => json({ count: marked ? 0 : 1 }),
    );

    await waitFor(() => {
      const calls = server.requests.map((r) => `${r.method} ${r.path}`);
      const read = calls.indexOf("POST /api/notices/read");
      expect(read).toBeGreaterThan(-1);
      expect(
        calls.lastIndexOf("GET /api/notices/unread-count"),
      ).toBeGreaterThan(read);
    });
    expect(await screen.findByRole("link", { name: "알림" })).toHaveTextContent(
      /^알림$/,
    );
  });

  it("다음 쪽을 받으면 그 쪽의 안 읽은 알림을 보낸다", async () => {
    const second: Notice = { ...OLD, id: 4, revision: 2, read: false };
    const { server, reads } = renderNotices((request) =>
      new URL(request.url).searchParams.get("before") === "5"
        ? json(page([second]))
        : json(page([GATHERED, OLD], 5)),
    );
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "더 보기" }));

    expect(await screen.findAllByRole("article")).toHaveLength(3);
    await waitFor(() =>
      expect(reads()).toEqual([
        { notices: [{ id: 12, revision: 3 }] },
        { notices: [{ id: 4, revision: 2 }] },
      ]),
    );
    expect(
      server.requests
        .filter((r) => r.method === "GET" && r.path === "/api/notices")
        .map((r) => r.search),
    ).toEqual(["", "?before=5"]);
    expect(
      screen.queryByRole("button", { name: "더 보기" }),
    ).not.toBeInTheDocument();
  });

  it("알림이 없으면 없다고 알리고 읽음을 보내지 않는다", async () => {
    const { reads } = renderNotices(() => json(page([])));

    expect(await screen.findByText("알림이 없습니다.")).toBeInTheDocument();
    expect(reads()).toEqual([]);
  });

  it("다시 들어오면 목록을 새로 받아 새 응답대로 강조한다", async () => {
    let read = false;
    const { router } = renderNotices(() => json(page([{ ...GATHERED, read }])));
    expect(await screen.findByText("새 알림")).toBeInTheDocument();

    read = true;
    await router.navigate("/");
    expect(await screen.findByText("첫 화면")).toBeInTheDocument();
    await router.navigate("/notices");

    expect(await screen.findAllByRole("article")).toHaveLength(1);
    expect(screen.queryByText("새 알림")).not.toBeInTheDocument();
  });

  it("목록이 401 이면 로그인 상태를 다시 읽어 로그인 화면으로 간다", async () => {
    const holder: { signOut?: () => void } = {};
    const { router, signOut } = renderNotices(() => {
      holder.signOut?.();
      return problem(401, "로그인이 필요하다");
    });
    holder.signOut = signOut;

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
    expect(router.state.location.search).toBe("?next=%2Fnotices");
  });

  it("목록을 읽지 못하면 다시 시도를 둔다", async () => {
    let fail = true;
    renderNotices(() => (fail ? problem(500, "서버 오류") : json(page([OLD]))));
    const user = userEvent.setup();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "알림을 읽지 못했습니다.",
    );
    fail = false;
    await user.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(await screen.findAllByRole("article")).toHaveLength(1);
  });
});
