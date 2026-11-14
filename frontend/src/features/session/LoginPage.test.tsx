import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { describe, expect, it } from "vitest";
import { LoginPage } from "@/features/session/LoginPage";
import { ApiClientContext, createApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";
import {
  fakeServer,
  type Handler,
  json,
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
  passwordChangeRequired: false,
  llmKeyRequired: false,
};

const SIGNED_IN: Me = {
  result: "SIGNED_IN",
  userId: 7,
  email: "alice@example.com",
  displayName: "앨리스",
  role: "USER",
  passwordChangeRequired: false,
  llmKeyRequired: false,
};

function renderLogin(
  signIn: Handler,
  {
    entry = "/login?next=%2Fdigest",
    me = (() => json(ANONYMOUS)) as Handler,
    signUpOpen = false,
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    }),
  } = {},
) {
  let signedIn = false;
  const server = fakeServer({
    "GET /api/me": (request) => (signedIn ? json(SIGNED_IN) : me(request)),
    "GET /api/sign-up-policy": () => json({ open: signUpOpen }),
    "POST /api/session": async (request) => {
      const response = await signIn(request);
      const body = (await response.clone().json()) as { result?: string };
      signedIn = body.result === "SIGNED_IN";
      return response;
    },
  });
  const router = createMemoryRouter(
    [
      { path: "/login", element: <LoginPage /> },
      { path: "/", element: <p>첫 화면</p> },
      { path: "/digest", element: <p>다이제스트</p> },
    ],
    { initialEntries: [entry] },
  );
  render(
    <QueryClientProvider client={queryClient}>
      <ApiClientContext.Provider
        value={createApiClient({ baseUrl: TEST_BASE_URL, fetch: server.fetch })}
      >
        <RouterProvider router={router} />
      </ApiClientContext.Provider>
    </QueryClientProvider>,
  );
  return { server, router };
}

async function fillAndSubmit(keep = false) {
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText("이메일"), "alice@example.com");
  await user.type(screen.getByLabelText("비밀번호"), "correct horse");
  if (keep) {
    await user.click(screen.getByRole("checkbox", { name: "로그인 유지" }));
  }
  await user.click(screen.getByRole("button", { name: "로그인" }));
}

describe("LoginPage", () => {
  it("복구 코드 재설정 링크를 두고, 공개 가입이 닫혔으면 가입 링크가 없다", async () => {
    renderLogin(() => json({}));

    expect(
      await screen.findByRole("link", { name: "복구 코드로 비밀번호 재설정" }),
    ).toHaveAttribute("href", "/recover");
    expect(
      screen.queryByRole("link", { name: "가입" }),
    ).not.toBeInTheDocument();
  });

  it("공개 가입이 열렸으면 가입 링크를 둔다", async () => {
    renderLogin(() => json({}), { signUpOpen: true });

    expect(await screen.findByRole("link", { name: "가입" })).toHaveAttribute(
      "href",
      "/sign-up",
    );
  });

  it("로그인하면 로그인 유지 선택을 보내고 next 로 간다", async () => {
    const { server, router } = renderLogin(() =>
      json({ result: "SIGNED_IN", retryAfterSeconds: null }),
    );

    await fillAndSubmit(true);

    expect(await screen.findByText("다이제스트")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/digest");
    const post = server.requests.find((r) => r.method === "POST");
    expect(JSON.parse(post?.body ?? "{}")).toEqual({
      email: "alice@example.com",
      password: "correct horse",
      keep: true,
    });
  });

  it("로그인하면 앞서 로그인했던 사용자의 알림 조회를 버린다", async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    queryClient.setQueryData(["notices", "unread"], 3);
    queryClient.setQueryData(["admin", "kept"], 1);
    renderLogin(() => json({ result: "SIGNED_IN", retryAfterSeconds: null }), {
      queryClient,
    });

    await fillAndSubmit();

    expect(await screen.findByText("다이제스트")).toBeInTheDocument();
    expect(queryClient.getQueryData(["notices", "unread"])).toBeUndefined();
    expect(queryClient.getQueryData(["admin", "kept"])).toBe(1);
  });

  it("로그인 유지는 기본으로 꺼져 있다", async () => {
    renderLogin(() => json({ result: "REJECTED", retryAfterSeconds: null }));

    expect(
      await screen.findByRole("checkbox", { name: "로그인 유지" }),
    ).not.toBeChecked();
  });

  it("거절이면 계정 유무를 가리지 않는 같은 문구를 보인다", async () => {
    renderLogin(() => json({ result: "REJECTED", retryAfterSeconds: null }));

    await fillAndSubmit();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "이메일 또는 비밀번호가 맞지 않습니다.",
    );
  });

  it("대기면 남은 시간을 보인다", async () => {
    renderLogin(() => json({ result: "WAIT", retryAfterSeconds: 64 }));

    await fillAndSubmit();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "로그인 시도가 많아 잠시 막혔습니다. 2분 뒤에 다시 시도해 주세요.",
    );
  });

  it("요청이 거부되면 상태를 다시 읽어 새 토큰을 받는다", async () => {
    const { server } = renderLogin(() =>
      problem(403, "요청 보호 토큰이 없거나 맞지 않는다"),
    );

    await fillAndSubmit();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "로그인 요청이 실패했습니다 (HTTP 403). 다시 시도해 주세요.",
    );
    expect(
      server.requests.filter((r) => r.method === "GET" && r.path === "/api/me"),
    ).toHaveLength(2);
  });

  it("이미 로그인했으면 next 로 보낸다", async () => {
    const { router } = renderLogin(
      () => json({ result: "SIGNED_IN", retryAfterSeconds: null }),
      { me: () => json(SIGNED_IN) },
    );

    expect(await screen.findByText("다이제스트")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/digest");
  });

  it("다른 호스트의 next 는 첫 화면으로 바꾼다", async () => {
    renderLogin(() => json({ result: "SIGNED_IN", retryAfterSeconds: null }), {
      entry: "/login?next=%2F%2Fevil.example",
    });

    await fillAndSubmit();

    expect(await screen.findByText("첫 화면")).toBeInTheDocument();
  });
});
