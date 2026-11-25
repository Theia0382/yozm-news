import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { describe, expect, it } from "vitest";
import { AdminLayout } from "@/features/admin/AdminLayout";
import { unwrap } from "@/features/admin/adminClient";
import { useAdminClient } from "@/features/admin/adminSession";
import { ApiClientContext, createApiClient } from "@/shared/api/client";
import {
  fakeServer,
  type Handler,
  json,
  noContent,
  problem,
  TEST_BASE_URL,
} from "@/test/fakeServer";
import { ANONYMOUS_ME, type Me, SIGNED_IN_ME } from "@/test/renderRoutes";

const ADMIN_ME: Me = {
  ...SIGNED_IN_ME,
  displayName: "운영자 앨리스",
  role: "ADMIN",
};

/** 운영자 틀 안에서 운영자 API 하나를 부르는 본문 — 운영자 클라이언트를 쓴다 */
function AdminBody() {
  const client = useAdminClient();
  return (
    <button
      type="button"
      onClick={() => {
        void unwrap(client.GET("/api/admin/presets")).catch(() => undefined);
      }}
    >
      프리셋 읽기
    </button>
  );
}

function renderAdmin(
  entry: string,
  me: () => Response,
  handlers: Record<string, Handler> = {},
) {
  let signedOut = false;
  const server = fakeServer({
    "GET /api/me": () => (signedOut ? json(ANONYMOUS_ME) : me()),
    "GET /api/notices/unread-count": () => json({ count: 2 }),
    "DELETE /api/session": () => {
      signedOut = true;
      return noContent();
    },
    ...handlers,
  });
  const router = createMemoryRouter(
    [
      {
        path: "/admin",
        element: <AdminLayout baseUrl={TEST_BASE_URL} fetch={server.fetch} />,
        children: [
          {
            index: true,
            element: (
              <>
                <p>운영자 본문</p>
                <AdminBody />
              </>
            ),
          },
          { path: "events", element: <p>사건 본문</p> },
        ],
      },
      { path: "/login", element: <p>로그인 화면</p> },
      { path: "/password", element: <p>비밀번호 변경 화면</p> },
      { path: "/", element: <p>첫 화면</p> },
    ],
    { initialEntries: [entry] },
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
  return { server, router };
}

describe("AdminLayout", () => {
  it("로그인하지 않았으면 돌아올 주소를 달아 로그인 화면으로 보낸다", async () => {
    const { router } = renderAdmin("/admin/events?from=2026-11-01", () =>
      json(ANONYMOUS_ME),
    );

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
    expect(router.state.location.search).toBe(
      "?next=%2Fadmin%2Fevents%3Ffrom%3D2026-11-01",
    );
    expect(screen.queryByText("사건 본문")).not.toBeInTheDocument();
  });

  it("비밀번호를 바꿔야 하면 돌아올 주소를 달아 비밀번호 변경 화면으로 보낸다", async () => {
    const { router } = renderAdmin("/admin", () =>
      json({ ...ADMIN_ME, passwordChangeRequired: true }),
    );

    expect(await screen.findByText("비밀번호 변경 화면")).toBeInTheDocument();
    expect(router.state.location.search).toBe("?next=%2Fadmin");
    expect(screen.queryByText("운영자 본문")).not.toBeInTheDocument();
  });

  it("운영자가 아니면 권한이 없다고 알리고 운영자 화면을 보이지 않는다", async () => {
    const { server } = renderAdmin("/admin", () => json(SIGNED_IN_ME));

    expect(
      await screen.findByRole("heading", { name: "운영자 권한이 없습니다" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "첫 화면으로" })).toHaveAttribute(
      "href",
      "/",
    );
    expect(screen.queryByText("운영자 본문")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("navigation", { name: "운영자 메뉴" }),
    ).not.toBeInTheDocument();
    expect(server.requests.some((r) => r.path.startsWith("/api/admin/"))).toBe(
      false,
    );
  });

  it("운영자면 LLM 키가 없어도 화면을 열고, 머리줄에 알림 · 사용자 화면 · 이름 · 로그아웃을 두며 토큰 입력이 없다", async () => {
    renderAdmin("/admin", () => json({ ...ADMIN_ME, llmKeyRequired: true }));

    expect(await screen.findByText("운영자 본문")).toBeInTheDocument();
    expect(
      await screen.findByRole("link", { name: "알림, 안 읽음 2개" }),
    ).toHaveAttribute("href", "/notices");
    expect(screen.getByRole("link", { name: "사용자 화면" })).toHaveAttribute(
      "href",
      "/",
    );
    expect(screen.getByText("운영자 앨리스")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "로그아웃" }),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText(/운영자 토큰/)).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "잠금" }),
    ).not.toBeInTheDocument();
  });

  it("운영자 API 가 401 이면 로그인 상태를 다시 읽어 로그인 화면으로 보낸다", async () => {
    let signedIn = true;
    const { server } = renderAdmin(
      "/admin",
      () => json(signedIn ? ADMIN_ME : ANONYMOUS_ME),
      {
        "GET /api/admin/presets": () => {
          signedIn = false;
          return problem(
            401,
            "로그인하지 않았거나 세션이 만료됐다",
            "urn:yozmnews:problem:unauthenticated",
          );
        },
      },
    );
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "프리셋 읽기" }),
    );

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
    expect(
      server.requests.filter((r) => r.path === "/api/me").length,
    ).toBeGreaterThanOrEqual(2);
    expect(
      server.requests.find((r) => r.path === "/api/admin/presets")
        ?.authorization,
    ).toBeNull();
  });

  it("로그아웃하면 세션을 지우고 로그인 화면으로 간다", async () => {
    const { server } = renderAdmin("/admin", () => json(ADMIN_ME));
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "로그아웃" }));

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
    await waitFor(() =>
      expect(
        server.requests.some(
          (r) => r.method === "DELETE" && r.path === "/api/session",
        ),
      ).toBe(true),
    );
  });

  it("로그아웃이 거부되면 운영자 화면에 남아 알린다", async () => {
    renderAdmin("/admin", () => json(ADMIN_ME), {
      "DELETE /api/session": () =>
        problem(
          403,
          "요청 보호 토큰(X-XSRF-TOKEN)이 없거나 맞지 않는다",
          "urn:yozmnews:problem:csrf-rejected",
        ),
    });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "로그아웃" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "로그아웃하지 못했습니다. 다시 시도해 주세요.",
    );
    expect(screen.getByText("운영자 본문")).toBeInTheDocument();
    expect(screen.queryByText("로그인 화면")).not.toBeInTheDocument();
  });

  it("상태를 읽지 못하면 다시 시도를 둔다", async () => {
    renderAdmin("/admin", () => problem(500, "서버 오류"));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "로그인 상태를 확인하지 못했습니다.",
    );
    expect(
      screen.getByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
  });

  it("열린 화면의 머리줄에 운영자 메뉴가 있고 지금 화면을 표시한다", async () => {
    renderAdmin("/admin", () => json(ADMIN_ME));

    const menu = await screen.findByRole("navigation", { name: "운영자 메뉴" });
    const sources = within(menu).getByRole("link", { name: "소스 · 피드" });
    expect(sources).toHaveAttribute("href", "/admin");
    expect(sources).toHaveAttribute("aria-current", "page");
    for (const [name, href] of [
      ["수집 현황", "/admin/collection"],
      ["평가 현황", "/admin/evaluation"],
      ["평가 결과", "/admin/evaluations"],
      ["표본 검수", "/admin/samples"],
      ["태그 체계", "/admin/tag-sets"],
      ["태그 기준", "/admin/thresholds"],
      ["사건", "/admin/events"],
      ["사건 흐름", "/admin/storylines"],
      ["큰 화제", "/admin/big-storylines"],
      ["판정 기록", "/admin/event-judgements"],
      ["이벤트 발행", "/admin/event-publications"],
      ["계정", "/admin/accounts"],
    ]) {
      const link = within(menu).getByRole("link", { name });
      expect(link).toHaveAttribute("href", href);
      expect(link).not.toHaveAttribute("aria-current");
    }
  });
});
