import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { describe, expect, it } from "vitest";
import { RequireSignIn } from "@/features/session/RequireSignIn";
import { ApiClientContext, createApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";
import {
  fakeServer,
  json,
  noContent,
  problem,
  TEST_BASE_URL,
} from "@/test/fakeServer";

type Me = components["schemas"]["MeResponse"];

const ANONYMOUS: Me = {
  result: "ANONYMOUS",
  userId: null,
  email: null,
  displayName: null,
  role: null,
};

const SIGNED_IN: Me = {
  result: "SIGNED_IN",
  userId: 7,
  email: "alice@example.com",
  displayName: "앨리스",
  role: "USER",
};

function renderGuarded(
  entry: string,
  me: () => Response,
  signOut: () => Response = noContent,
  unread: () => Response = () => json({ count: 0 }),
) {
  let signedOut = false;
  const server = fakeServer({
    "GET /api/me": () => (signedOut ? json(ANONYMOUS) : me()),
    "DELETE /api/session": () => {
      const response = signOut();
      signedOut = response.ok;
      return response;
    },
    "GET /api/notices/unread-count": unread,
  });
  const router = createMemoryRouter(
    [
      {
        element: <RequireSignIn />,
        children: [
          { path: "/", element: <p>첫 화면</p> },
          { path: "/digest", element: <p>다이제스트</p> },
        ],
      },
      { path: "/login", element: <p>로그인 화면</p> },
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

describe("RequireSignIn", () => {
  it("로그인하지 않았으면 돌아올 주소를 달아 로그인 화면으로 보낸다", async () => {
    const { router } = renderGuarded("/digest?d=2026-11-13", () =>
      json(ANONYMOUS),
    );

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
    expect(router.state.location.search).toBe(
      "?next=%2Fdigest%3Fd%3D2026-11-13",
    );
    expect(screen.queryByText("다이제스트")).not.toBeInTheDocument();
  });

  it("로그인했으면 이름과 로그아웃을 두고 화면을 보인다", async () => {
    renderGuarded("/digest", () => json(SIGNED_IN));

    expect(await screen.findByText("다이제스트")).toBeInTheDocument();
    expect(screen.getByText("앨리스")).toBeInTheDocument();
  });

  it("로그아웃하면 세션을 지우고 로그인 화면으로 가며 보호 화면을 다시 보이지 않는다", async () => {
    const { server, router } = renderGuarded("/digest", () => json(SIGNED_IN));
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "로그아웃" }));

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
    expect(
      server.requests.some(
        (r) => r.method === "DELETE" && r.path === "/api/session",
      ),
    ).toBe(true);

    await router.navigate(-1);
    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
    expect(screen.queryByText("다이제스트")).not.toBeInTheDocument();
  });

  it("로그아웃이 거부되면 로그인한 화면에 남아 알린다", async () => {
    const { server, router } = renderGuarded(
      "/digest",
      () => json(SIGNED_IN),
      () => problem(403, "요청 보호 토큰이 없거나 맞지 않는다"),
    );
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "로그아웃" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "로그아웃하지 못했습니다. 다시 시도해 주세요.",
    );
    expect(router.state.location.pathname).toBe("/digest");
    expect(screen.getByText("다이제스트")).toBeInTheDocument();
    expect(
      server.requests.filter((r) => r.method === "GET" && r.path === "/api/me"),
    ).toHaveLength(2);
  });

  it("안 읽은 알림 수를 알림 링크에 붙인다", async () => {
    renderGuarded(
      "/digest",
      () => json(SIGNED_IN),
      noContent,
      () => json({ count: 3 }),
    );

    const link = await screen.findByRole("link", { name: "알림, 안 읽음 3개" });
    expect(link).toHaveTextContent("알림 3");
    expect(link).toHaveAttribute("href", "/notices");
  });

  it("안 읽은 알림이 없으면 수 없이 알림만 보인다", async () => {
    const { server } = renderGuarded("/digest", () => json(SIGNED_IN));

    expect(await screen.findByText("다이제스트")).toBeInTheDocument();
    await waitFor(() =>
      expect(
        server.requests.filter((r) => r.path === "/api/notices/unread-count"),
      ).toHaveLength(1),
    );
    expect(screen.getByRole("link", { name: "알림" })).toHaveTextContent(
      /^알림$/,
    );
  });

  it("수를 읽지 못하면 수 없이 알림만 보인다", async () => {
    const { server } = renderGuarded(
      "/digest",
      () => json(SIGNED_IN),
      noContent,
      () => problem(500, "서버 오류"),
    );

    expect(await screen.findByText("다이제스트")).toBeInTheDocument();
    await waitFor(() =>
      expect(
        server.requests.filter((r) => r.path === "/api/notices/unread-count"),
      ).not.toHaveLength(0),
    );
    expect(screen.getByRole("link", { name: "알림" })).toHaveTextContent(
      /^알림$/,
    );
  });

  it("안 읽은 알림이 99개를 넘으면 99+ 로 줄인다", async () => {
    renderGuarded(
      "/digest",
      () => json(SIGNED_IN),
      noContent,
      () => json({ count: 150 }),
    );

    expect(
      await screen.findByRole("link", { name: "알림, 안 읽음 150개" }),
    ).toHaveTextContent("알림 99+");
  });

  it("화면을 옮기면 안 읽은 알림 수를 다시 읽고, 같은 화면에서는 다시 읽지 않는다", async () => {
    let count = 0;
    const { server, router } = renderGuarded(
      "/",
      () => json(SIGNED_IN),
      noContent,
      () => json({ count }),
    );
    const unreadRequests = () =>
      server.requests.filter((r) => r.path === "/api/notices/unread-count");

    expect(await screen.findByText("첫 화면")).toBeInTheDocument();
    await waitFor(() => expect(unreadRequests()).toHaveLength(1));
    await router.navigate("/?again=1");
    expect(await screen.findByText("첫 화면")).toBeInTheDocument();
    expect(unreadRequests()).toHaveLength(1);

    count = 2;
    await router.navigate("/digest");

    expect(
      await screen.findByRole("link", { name: "알림, 안 읽음 2개" }),
    ).toBeInTheDocument();
    expect(unreadRequests()).toHaveLength(2);
  });

  it("로그인하지 않았으면 안 읽은 알림 수를 묻지 않는다", async () => {
    const { server } = renderGuarded("/digest", () => json(ANONYMOUS));

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
    expect(
      server.requests.filter((r) => r.path === "/api/notices/unread-count"),
    ).toHaveLength(0);
  });

  it("상태를 읽지 못하면 다시 시도를 둔다", async () => {
    renderGuarded("/", () => problem(500, "서버 오류"));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "로그인 상태를 확인하지 못했습니다.",
    );
    expect(
      screen.getByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
  });
});
