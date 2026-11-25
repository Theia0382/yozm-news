import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { describe, expect, it } from "vitest";
import type { DigestRequestStatus } from "@/features/digests/digestQueries";
import { home, status } from "@/features/digests/digestTestKit";
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
  reportFailed: null,
  revision: 3,
  createdAt: "2026-11-13T00:00:00Z",
  updatedAt: "2026-11-13T02:00:00Z",
  read: false,
  digestFailed: null,
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
  preferencesAdjusted: null,
  storylineSubscriptionsRemoved: null,
};

/** 읽은 피드 상태 알림 하나 */
const OLD: Notice = {
  id: 5,
  kind: "FEED_STATUS",
  reportFailed: null,
  revision: 1,
  createdAt: "2026-11-12T00:00:00Z",
  updatedAt: "2026-11-12T00:00:00Z",
  read: true,
  digestFailed: null,
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
  preferencesAdjusted: null,
  storylineSubscriptionsRemoved: null,
};

/** 복구 코드로 재설정한 비밀번호 알림 */
const PASSWORD: Notice = {
  id: 20,
  kind: "PASSWORD_CHANGED",
  reportFailed: null,
  revision: 1,
  createdAt: "2026-11-13T03:00:00Z",
  updatedAt: "2026-11-13T03:00:00Z",
  read: false,
  digestFailed: null,
  feedStatus: null,
  passwordChanged: { method: "RECOVERY", changedAt: "2026-11-13T03:00:00Z" },
  keyInvalidated: null,
  preferencesAdjusted: null,
  storylineSubscriptionsRemoved: null,
};

/** 공급자가 받아 주지 않아 무효가 된 키의 알림 */
const KEY_INVALIDATED: Notice = {
  id: 30,
  kind: "KEY_INVALIDATED",
  reportFailed: null,
  revision: 1,
  createdAt: "2026-11-13T03:00:05Z",
  updatedAt: "2026-11-13T03:00:05Z",
  read: false,
  digestFailed: null,
  feedStatus: null,
  passwordChanged: null,
  keyInvalidated: {
    provider: "ANTHROPIC",
    invalidatedAt: "2026-11-13T03:00:00Z",
  },
  preferencesAdjusted: null,
  storylineSubscriptionsRemoved: null,
};

/** 태그 체계 버전 2 를 따라 해제 · 더함이 있었던 선호 조정 알림 */
const PREFERENCES_ADJUSTED: Notice = {
  id: 40,
  kind: "PREFERENCES_ADJUSTED",
  reportFailed: null,
  revision: 1,
  createdAt: "2026-11-13T04:00:00Z",
  updatedAt: "2026-11-13T04:00:00Z",
  read: false,
  digestFailed: null,
  feedStatus: null,
  passwordChanged: null,
  keyInvalidated: null,
  preferencesAdjusted: {
    tagSetNumber: 2,
    adjustments: [
      {
        kind: "REGION",
        key: "china",
        name: "중국",
        mode: "EXCLUDE",
        change: "RELEASED_REMOVED",
      },
      {
        kind: "FORMAT",
        key: "listicle",
        name: "목록형",
        mode: "EXCLUDE",
        change: "ADDED_DEFAULT_EXCLUDED",
      },
    ],
  },
  storylineSubscriptionsRemoved: null,
};

/** 구독한 스토리라인 둘이 사라진 알림 — 하나는 남았던 기사의 제목이 있고, 하나는 알 수 없다 */
const SUBSCRIPTIONS_REMOVED: Notice = {
  id: 50,
  kind: "STORYLINE_SUBSCRIPTIONS_REMOVED",
  reportFailed: null,
  revision: 1,
  createdAt: "2026-11-13T05:00:00Z",
  updatedAt: "2026-11-13T05:00:00Z",
  read: false,
  digestFailed: null,
  feedStatus: null,
  passwordChanged: null,
  keyInvalidated: null,
  preferencesAdjusted: null,
  storylineSubscriptionsRemoved: {
    removed: [
      { storylineId: 31, title: "의대 정원 조정안 발표" },
      { storylineId: 32, title: null },
    ],
  },
};

/** 키를 쓸 수 없어 실패한 일정 다이제스트의 알림 */
const DIGEST_FAILED_KEY: Notice = {
  id: 60,
  kind: "DIGEST_FAILED",
  reportFailed: null,
  revision: 1,
  createdAt: "2026-11-18T22:03:11Z",
  updatedAt: "2026-11-18T22:03:11Z",
  read: false,
  digestFailed: {
    digestDate: "2026-11-19",
    cause: "KEY",
    failedAt: "2026-11-18T22:03:10Z",
  },
  feedStatus: null,
  passwordChanged: null,
  keyInvalidated: null,
  preferencesAdjusted: null,
  storylineSubscriptionsRemoved: null,
};

/** 공급자의 일시 장애로 실패한 다이제스트의 알림 */
const DIGEST_FAILED_TEMPORARY: Notice = {
  ...DIGEST_FAILED_KEY,
  id: 61,
  digestFailed: {
    digestDate: "2026-11-20",
    cause: "TEMPORARY",
    failedAt: "2026-11-19T22:03:10Z",
  },
};

/** 모델 출력을 쓰지 못해 실패한 월간 리포트의 알림 */
const REPORT_FAILED_OUTPUT: Notice = {
  id: 70,
  kind: "REPORT_FAILED",
  reportFailed: {
    reportId: 905,
    kind: "MONTH",
    firstDay: "2026-10-01",
    lastDay: "2026-10-31",
    cause: "OUTPUT",
    failedAt: "2026-11-01T22:05:00Z",
  },
  revision: 1,
  createdAt: "2026-11-01T22:05:01Z",
  updatedAt: "2026-11-01T22:05:01Z",
  read: false,
  digestFailed: null,
  feedStatus: null,
  passwordChanged: null,
  keyInvalidated: null,
  preferencesAdjusted: null,
  storylineSubscriptionsRemoved: null,
};

function page(notices: Notice[], nextBefore: number | null = null): NoticePage {
  return { notices, nextBefore };
}

/** 홈 응답 — 해결되지 않은 마지막 실패만 바꾼다 */
function homeWith(
  unresolvedFailure: DigestRequestStatus["unresolvedFailure"],
): Handler {
  return () => json(home({ status: status({ unresolvedFailure }) }));
}

function renderNotices(
  notices: Handler,
  read: Handler = () => noContent(),
  unread: Handler = () => json({ count: 0 }),
  options: { digests?: Handler; productionRetry?: boolean } = {},
) {
  let signedIn = true;
  const server = fakeServer({
    "GET /api/me": () => json(signedIn ? SIGNED_IN : ANONYMOUS),
    "GET /api/notices/unread-count": unread,
    "GET /api/notices": notices,
    "POST /api/notices/read": read,
    "GET /api/digests/latest": options.digests ?? homeWith(null),
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
        // 운영과 같은 재시도(기본 3번)를 보는 시험은 기다림만 없앤다
        options.productionRetry
          ? new QueryClient({ defaultOptions: { queries: { retryDelay: 0 } } })
          : new QueryClient({ defaultOptions: { queries: { retry: false } } })
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

  it("선호 조정 알림은 버전과 조정 항목을 보이고 관심 설정으로 잇는다", async () => {
    renderNotices(() => json(page([PREFERENCES_ADJUSTED])));

    const item = await screen.findByRole("article");
    expect(within(item).getByRole("heading", { level: 2 })).toHaveTextContent(
      "새 알림태그 체계가 바뀌어 관심 설정을 맞췄습니다",
    );
    expect(item).toHaveTextContent(
      "태그 체계 버전 2에 맞춰 관심 설정이 바뀌었습니다.",
    );
    expect(item).toHaveTextContent("지역 중국 — 태그가 없어져 풀었습니다");
    expect(item).toHaveTextContent(
      "형식 목록형 — 새로 생긴 기본 제외 형식이라 제외에 더했습니다",
    );
    expect(
      within(item).getByRole("link", { name: "관심 설정에서 확인하기" }),
    ).toHaveAttribute("href", "/interests");
    // 추적 중인 흐름 링크는 사라진 구독 알림에만 있다
    expect(
      within(item).queryByRole("link", { name: "추적 중인 흐름 보기" }),
    ).not.toBeInTheDocument();
  });

  it("사라진 구독 알림은 스토리라인마다 남았던 기사 제목을 보이고 없으면 알 수 없다고 한다", async () => {
    renderNotices(() => json(page([SUBSCRIPTIONS_REMOVED])));

    const item = await screen.findByRole("article");
    expect(within(item).getByRole("heading", { level: 2 })).toHaveTextContent(
      "새 알림추적하던 흐름이 사라졌습니다",
    );
    expect(item).toHaveTextContent(
      "추적하던 흐름이 더 이어지지 않아 추적에서 뺐습니다.",
    );
    const rows = within(item).getAllByRole("listitem");
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent("의대 정원 조정안 발표");
    expect(rows[1]).toHaveTextContent("제목을 알 수 없는 흐름");
    expect(
      within(item).getByRole("link", { name: "추적 중인 흐름 보기" }),
    ).toHaveAttribute("href", "/storylines/subscriptions");
  });

  it("다이제스트 실패 알림은 날짜 · 시각과 할 일을 보이고 그 날짜 다이제스트로, 키 갈래면 키 화면으로 잇는다", async () => {
    renderNotices(() => json(page([DIGEST_FAILED_KEY])));

    const item = await screen.findByRole("article");
    expect(within(item).getByRole("heading", { level: 2 })).toHaveTextContent(
      "새 알림다이제스트를 만들지 못했습니다",
    );
    expect(item).toHaveTextContent(
      "2026. 11. 19. 다이제스트 · 2026. 11. 19. 오전 7:03 GMT+9",
    );
    expect(item).toHaveTextContent(
      "LLM 키를 쓸 수 없었습니다. 키를 확인하거나 다시 등록하세요.",
    );
    expect(
      within(item).getByRole("link", { name: "LLM 키 화면에서 확인하기" }),
    ).toHaveAttribute("href", "/keys");
    expect(
      within(item).getByRole("link", { name: "그 날짜 다이제스트 보기" }),
    ).toHaveAttribute("href", "/digests/2026-11-19");
    // 키 갈래의 할 일은 다시 만들기가 아니다
    expect(
      within(item).queryByRole("link", {
        name: "오늘 다이제스트에서 다시 만들기",
      }),
    ).toBeNull();
  });

  it("리포트 실패 알림은 종류 · 기간 · 시각과 리포트의 할 일을 보이고 그 리포트로, 출력 갈래면 모델 고르기로 잇는다", async () => {
    renderNotices(() => json(page([REPORT_FAILED_OUTPUT])));

    const item = await screen.findByRole("article");
    expect(within(item).getByRole("heading", { level: 2 })).toHaveTextContent(
      "새 알림변화 리포트를 만들지 못했습니다",
    );
    expect(item).toHaveTextContent(
      "월간 리포트 · 2026. 10. 01. ~ 2026. 10. 31. · 2026. 11. 02. 오전 7:05 GMT+9",
    );
    expect(item).toHaveTextContent(
      "모델의 출력을 리포트로 쓰지 못했습니다. 관심 설정에서 다른 모델을 골라 볼 수 있습니다.",
    );
    expect(
      within(item).getByRole("link", { name: "그 리포트 보기" }),
    ).toHaveAttribute("href", "/reports/905");
    expect(
      within(item).getByRole("link", { name: "관심 설정에서 모델 고르기" }),
    ).toHaveAttribute("href", "/interests#interest-model");
    // 다이제스트의 할 일(오늘 다이제스트에서 다시 만들기)은 리포트 알림에 없다
    expect(item).not.toHaveTextContent("다이제스트");
  });

  it("해결되지 않은 마지막 실패인 일시 장애 알림은 다시 만들기가 있는 오늘 다이제스트로 잇고 키 화면으로 잇지 않는다", async () => {
    renderNotices(
      () => json(page([DIGEST_FAILED_TEMPORARY])),
      undefined,
      undefined,
      {
        digests: homeWith({
          digestDate: "2026-11-20",
          failedAt: "2026-11-19T22:03:10Z",
        }),
      },
    );

    const item = await screen.findByRole("article");
    expect(item).toHaveTextContent("2026. 11. 20. 다이제스트");
    expect(item).toHaveTextContent(
      "공급자가 잠시 답하지 못했거나 생성이 끝나지 않았습니다. 잠시 뒤 오늘 다이제스트에서 다시 만들어 보세요.",
    );
    await waitFor(() =>
      expect(
        within(item)
          .getAllByRole("link")
          .map((link) => [link.textContent, link.getAttribute("href")]),
      ).toEqual([
        ["그 날짜 다이제스트 보기", "/digests/2026-11-20"],
        ["오늘 다이제스트에서 다시 만들기", "/"],
      ]),
    );
  });

  it("다시 만들기 링크는 해결되지 않은 마지막 실패의 알림에만 붙는다", async () => {
    const older: Notice = {
      ...DIGEST_FAILED_KEY,
      id: 62,
      digestFailed: {
        digestDate: "2026-11-19",
        cause: "OUTPUT",
        failedAt: "2026-11-18T22:03:10Z",
      },
    };
    const { server } = renderNotices(
      () => json(page([DIGEST_FAILED_TEMPORARY, older])),
      undefined,
      undefined,
      {
        digests: homeWith({
          digestDate: "2026-11-20",
          failedAt: "2026-11-19T22:03:10Z",
        }),
      },
    );

    const items = await screen.findAllByRole("article");
    await waitFor(() =>
      expect(
        within(items[0]!).getByRole("link", {
          name: "오늘 다이제스트에서 다시 만들기",
        }),
      ).toHaveAttribute("href", "/"),
    );
    expect(
      within(items[1]!).queryByRole("link", {
        name: "오늘 다이제스트에서 다시 만들기",
      }),
    ).toBeNull();
    // 출력 갈래는 해결 여부와 무관하게 관심 설정의 모델 절로 잇는다
    expect(
      within(items[1]!).getByRole("link", {
        name: "관심 설정에서 모델 고르기",
      }),
    ).toHaveAttribute("href", "/interests#interest-model");
    // 실패 알림이 둘이어도 홈 응답은 한 번 읽는다
    expect(
      server.requests.filter((r) => r.path === "/api/digests/latest"),
    ).toHaveLength(1);
  });

  it("날짜가 같아도 끝낸 시각이 다르면 다시 만들기 링크를 달지 않는다", async () => {
    const { server } = renderNotices(
      () => json(page([DIGEST_FAILED_TEMPORARY])),
      undefined,
      undefined,
      {
        digests: homeWith({
          digestDate: "2026-11-20",
          failedAt: "2026-11-19T23:30:00Z",
        }),
      },
    );

    const item = await screen.findByRole("article");
    await waitFor(() =>
      expect(
        server.requests.some((r) => r.path === "/api/digests/latest"),
      ).toBe(true),
    );
    await waitFor(() =>
      expect(within(item).getAllByRole("link")).toHaveLength(1),
    );
    expect(
      within(item).queryByRole("link", {
        name: "오늘 다이제스트에서 다시 만들기",
      }),
    ).toBeNull();
  });

  it("다이제스트 응답을 읽지 못하면 다시 만들기 링크만 달지 않는다", async () => {
    const { server } = renderNotices(
      () => json(page([DIGEST_FAILED_TEMPORARY])),
      undefined,
      undefined,
      { digests: () => problem(403, "llm-key-required") },
    );

    const item = await screen.findByRole("article");
    await waitFor(() =>
      expect(
        server.requests.some((r) => r.path === "/api/digests/latest"),
      ).toBe(true),
    );
    expect(
      within(item).getByRole("link", { name: "그 날짜 다이제스트 보기" }),
    ).toHaveAttribute("href", "/digests/2026-11-20");
    expect(
      within(item).queryByRole("link", {
        name: "오늘 다이제스트에서 다시 만들기",
      }),
    ).toBeNull();
  });

  it("홈 응답이 403 이면 다시 묻지 않는다", async () => {
    const { server } = renderNotices(
      () => json(page([DIGEST_FAILED_TEMPORARY])),
      undefined,
      undefined,
      {
        digests: () => problem(403, "llm-key-required"),
        productionRetry: true,
      },
    );

    await screen.findByRole("article");
    await waitFor(() =>
      expect(
        server.requests.filter((r) => r.path === "/api/digests/latest"),
      ).toHaveLength(1),
    );
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(
      server.requests.filter((r) => r.path === "/api/digests/latest"),
    ).toHaveLength(1);
  });

  it("홈 응답이 그 밖의 오류면 운영의 재시도대로 다시 묻는다", async () => {
    const { server } = renderNotices(
      () => json(page([DIGEST_FAILED_TEMPORARY])),
      undefined,
      undefined,
      { digests: () => problem(500, "server-error"), productionRetry: true },
    );

    await screen.findByRole("article");
    await waitFor(() =>
      expect(
        server.requests.filter((r) => r.path === "/api/digests/latest"),
      ).toHaveLength(4),
    );
  });

  it.each([
    ["OUTPUT", true],
    ["PROVIDER_ACCOUNT", false],
  ] as const)(
    "%s 갈래가 해결되지 않은 마지막 실패면 다시 만들기 링크 — %s",
    async (cause, retry) => {
      renderNotices(
        () =>
          json(
            page([
              {
                ...DIGEST_FAILED_KEY,
                digestFailed: {
                  digestDate: "2026-11-21",
                  cause,
                  failedAt: "2026-11-20T22:03:10Z",
                },
              },
            ]),
          ),
        undefined,
        undefined,
        {
          digests: homeWith({
            digestDate: "2026-11-21",
            failedAt: "2026-11-20T22:03:10Z",
          }),
        },
      );

      const item = await screen.findByRole("article");
      expect(
        within(item).getByRole("link", { name: "그 날짜 다이제스트 보기" }),
      ).toHaveAttribute("href", "/digests/2026-11-21");
      await waitFor(() =>
        expect(
          within(item).queryByRole("link", {
            name: "오늘 다이제스트에서 다시 만들기",
          }) !== null,
        ).toBe(retry),
      );
      expect(
        within(item).queryByRole("link", { name: "LLM 키 화면에서 확인하기" }),
      ).toBeNull();
    },
  );

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
    const { router, server } = renderNotices(() =>
      json(page([{ ...GATHERED, read }])),
    );
    const listRequests = () =>
      server.requests.filter(
        (request) =>
          request.method === "GET" && request.path === "/api/notices",
      ).length;
    expect(await screen.findByText("새 알림")).toBeInTheDocument();

    read = true;
    await router.navigate("/");
    expect(await screen.findByText("첫 화면")).toBeInTheDocument();
    await router.navigate("/notices");

    // 다시 받은 응답이 그려지기 전에는 앞 화면의 목록이 잠깐 남을 수 있다 — 새 요청과 그 응답의 강조를 기다린다
    await waitFor(() => {
      expect(listRequests()).toBe(2);
      expect(screen.getAllByRole("article")).toHaveLength(1);
      expect(screen.queryByText("새 알림")).not.toBeInTheDocument();
    });
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
