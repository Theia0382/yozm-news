import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { afterEach, describe, expect, it } from "vitest";
import { createQueryClient } from "@/app/queryClient";
import { LoginPage } from "@/features/session/LoginPage";
import { ME_QUERY_KEY } from "@/features/session/sessionQueries";
import { ApiClientContext, createApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";
import {
  fakeServer,
  type Handler,
  json,
  problem,
  TEST_BASE_URL,
} from "@/test/fakeServer";
import {
  installFakeTurnstile,
  uninstallFakeTurnstile,
} from "@/test/fakeTurnstile";

type Me = components["schemas"]["MeResponse"];

type HumanCheck = components["schemas"]["HumanCheckResponse"];

const TURNSTILE: HumanCheck = {
  provider: "TURNSTILE",
  siteKey: "1x00000000000000000000AA",
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
    humanCheck = { provider: "OFF", siteKey: null } as HumanCheck,
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    }),
  } = {},
) {
  let signedIn = false;
  const server = fakeServer({
    "GET /api/me": (request) => (signedIn ? json(SIGNED_IN) : me(request)),
    "GET /api/sign-up-policy": () => json({ open: signUpOpen }),
    "GET /api/human-check": () => json(humanCheck),
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

  it("다른 사용자로 로그인하면 앞 사용자의 조회를 로그인 상태 밖은 모두 비운다", async () => {
    const queryClient = createQueryClient();
    queryClient.setDefaultOptions({ queries: { retry: false } });
    renderLogin(() => json({ result: "SIGNED_IN", retryAfterSeconds: null }), {
      queryClient,
    });
    // 로그인 화면이 익명을 읽은 뒤에 앞 사용자의 조회를 넣는다 — 익명 → 익명은 비우지 않으므로 비움의 계기는 로그인뿐이다
    await waitFor(() =>
      expect(queryClient.getQueryData(ME_QUERY_KEY)).toEqual(ANONYMOUS),
    );
    queryClient.setQueryData(["notices", "unread"], 3);
    queryClient.setQueryData(["llm-keys"], { providers: [] });
    queryClient.setQueryData(["interests", "settings"], {
      timezone: "Europe/Paris",
    });
    queryClient.setQueryData(["admin", "kept"], 1);
    expect(queryClient.getQueryData(["notices", "unread"])).toBe(3);
    expect(queryClient.getQueryData(["llm-keys"])).toEqual({ providers: [] });
    expect(queryClient.getQueryData(["interests", "settings"])).toEqual({
      timezone: "Europe/Paris",
    });
    expect(queryClient.getQueryData(["admin", "kept"])).toBe(1);

    await fillAndSubmit();

    expect(await screen.findByText("다이제스트")).toBeInTheDocument();
    expect(queryClient.getQueryData(["notices", "unread"])).toBeUndefined();
    expect(queryClient.getQueryData(["llm-keys"])).toBeUndefined();
    expect(queryClient.getQueryData(["interests", "settings"])).toBeUndefined();
    expect(queryClient.getQueryData(["admin", "kept"])).toBeUndefined();
    expect(queryClient.getQueryData(ME_QUERY_KEY)).toEqual(SIGNED_IN);
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

  it("요청 제한에 걸리면 실패 지연과 다른 요청 제한 안내를 보인다", async () => {
    renderLogin(
      () =>
        new Response(
          JSON.stringify({
            type: "urn:yozmnews:problem:rate-limited",
            title: "요청이 너무 많다",
            status: 429,
            detail: "",
            instance: "/api/session",
          }),
          {
            status: 429,
            headers: {
              "Content-Type": "application/problem+json",
              "Retry-After": "540",
            },
          },
        ),
    );

    await fillAndSubmit();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "이 네트워크에서 요청이 너무 많아 잠시 막혔습니다. 9분 뒤에 다시 시도해 주세요.",
    );
  });
});

describe("LoginPage 봇 차단", () => {
  afterEach(() => uninstallFakeTurnstile());

  it("끈 인스턴스는 위젯 없이 토큰 헤더 없이 보낸다", async () => {
    const { server } = renderLogin(() =>
      json({ result: "REJECTED", retryAfterSeconds: null }),
    );

    await fillAndSubmit();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "이메일 또는 비밀번호가 맞지 않습니다.",
    );
    expect(screen.queryByTestId("turnstile-widget")).not.toBeInTheDocument();
    const signIn = server.requests.find((r) => r.path === "/api/session");
    expect(signIn?.headers.get("X-Human-Check")).toBeNull();
  });

  it("켠 인스턴스는 확인을 마쳐야 제출하고, 토큰을 헤더로 실은 뒤 위젯을 다시 받는다", async () => {
    const turnstile = installFakeTurnstile();
    const { server } = renderLogin(
      () => json({ result: "REJECTED", retryAfterSeconds: null }),
      { humanCheck: TURNSTILE },
    );
    const user = userEvent.setup();

    await user.type(
      await screen.findByLabelText("이메일"),
      "alice@example.com",
    );
    await user.type(screen.getByLabelText("비밀번호"), "correct horse");
    expect(
      await screen.findByText("사람 확인을 마치면 제출할 수 있습니다."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "로그인" })).toBeDisabled();
    expect(turnstile.rendered[0]).toMatchObject({
      sitekey: "1x00000000000000000000AA",
      action: "sign-in",
    });

    turnstile.solve("0.human-token");
    await user.click(screen.getByRole("button", { name: "로그인" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "이메일 또는 비밀번호가 맞지 않습니다.",
    );
    const signIn = server.requests.find((r) => r.path === "/api/session");
    expect(signIn?.headers.get("X-Human-Check")).toBe("0.human-token");
    // 토큰은 한 번만 쓰인다 — 제출 뒤 위젯을 다시 받고, 다시 확인할 때까지 제출할 수 없다
    expect(turnstile.resets).toEqual(["widget-1"]);
    expect(screen.getByRole("button", { name: "로그인" })).toBeDisabled();
  });

  it("사람 확인에 실패하면 그 안내를 보인다", async () => {
    const turnstile = installFakeTurnstile();
    renderLogin(
      () => problem(403, "", "urn:yozmnews:problem:human-check-failed"),
      { humanCheck: TURNSTILE },
    );

    await screen.findByText("사람 확인을 마치면 제출할 수 있습니다.");
    turnstile.solve("0.spent-token");
    await fillAndSubmit();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "사람 확인에 실패했습니다. 확인을 다시 마친 뒤 제출해 주세요.",
    );
  });

  it("위젯 스크립트를 받지 못하면 제출할 수 없는 까닭을 말한다", async () => {
    renderLogin(() => json({}), { humanCheck: TURNSTILE });

    const script = await waitForScript();
    script.dispatchEvent(new Event("error"));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "사람 확인을 불러오지 못해 지금은 제출할 수 없습니다.",
    );
    expect(screen.getByRole("button", { name: "로그인" })).toBeDisabled();
  });
});

/** 위젯이 넣은 Turnstile 스크립트 */
async function waitForScript(): Promise<HTMLScriptElement> {
  for (let i = 0; i < 50; i++) {
    const script = document.head.querySelector<HTMLScriptElement>(
      'script[src^="https://challenges.cloudflare.com/turnstile/v0/api.js"]',
    );
    if (script) {
      return script;
    }
    await new Promise((resolve) => setTimeout(resolve, 10));
  }
  throw new Error("Turnstile 스크립트를 넣지 않았다");
}
