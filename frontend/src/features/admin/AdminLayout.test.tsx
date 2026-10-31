import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { afterEach, describe, expect, it } from "vitest";
import { AdminLayout } from "@/features/admin/AdminLayout";
import {
  fakeServer,
  type Handler,
  json,
  problem,
  TEST_BASE_URL,
} from "@/features/admin/adminTestServer";

const TOKEN = "f0e1d2c3b4a5968778695a4b3c2d1e0f";
const STORAGE_KEY = "yozmnews.admin.token";

function renderAdmin(presets: Handler) {
  const server = fakeServer({ "GET /api/admin/presets": presets });
  const router = createMemoryRouter(
    [
      {
        path: "/admin",
        element: <AdminLayout baseUrl={TEST_BASE_URL} fetch={server.fetch} />,
        children: [{ index: true, element: <p>운영자 본문</p> }],
      },
    ],
    { initialEntries: ["/admin"] },
  );
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return server;
}

/** 토큰이 맞을 때만 목록을 주는 서버 */
const checksToken: Handler = (request) =>
  request.headers.get("Authorization") === `Bearer ${TOKEN}`
    ? json([])
    : problem(401, "운영자 토큰이 없거나 맞지 않는다");

describe("AdminLayout", () => {
  afterEach(() => sessionStorage.clear());

  it("운영자 기능이 닫힌 인스턴스면 닫힘을 안내한다", async () => {
    const server = renderAdmin(() =>
      problem(403, "닫힘", "urn:yozmnews:problem:admin-closed"),
    );

    expect(
      await screen.findByRole("heading", {
        name: "운영자 기능이 닫혀 있습니다",
      }),
    ).toBeInTheDocument();
    expect(screen.getByText("YOZMNEWS_ADMIN_TOKEN")).toBeInTheDocument();
    expect(server.requests.map((r) => r.authorization)).toEqual([null]);
  });

  it("토큰이 없으면 서버에 물은 뒤 토큰 입력을 보인다", async () => {
    renderAdmin(checksToken);

    expect(
      await screen.findByRole("heading", { name: "운영자 토큰" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("맞는 토큰을 넣으면 화면을 열고 탭에 보관하며, 잠그면 지운다", async () => {
    const user = userEvent.setup();
    renderAdmin(checksToken);

    await user.type(
      await screen.findByLabelText("서버에 설정한 운영자 토큰"),
      TOKEN,
    );
    await user.click(screen.getByRole("button", { name: "열기" }));

    expect(await screen.findByText("운영자 본문")).toBeInTheDocument();
    expect(sessionStorage.getItem(STORAGE_KEY)).toBe(TOKEN);

    await user.click(screen.getByRole("button", { name: "잠금" }));

    expect(
      await screen.findByRole("heading", { name: "운영자 토큰" }),
    ).toBeInTheDocument();
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it("보관한 토큰이 맞지 않으면 지우고 다시 입력하게 한다", async () => {
    sessionStorage.setItem(STORAGE_KEY, "stale-token-0123456789abcdef0123");
    renderAdmin(checksToken);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "토큰이 맞지 않습니다. 다시 입력해 주세요.",
    );
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it("보관한 토큰이 맞으면 입력 없이 연다", async () => {
    sessionStorage.setItem(STORAGE_KEY, TOKEN);
    renderAdmin(checksToken);

    expect(await screen.findByText("운영자 본문")).toBeInTheDocument();
  });
});
