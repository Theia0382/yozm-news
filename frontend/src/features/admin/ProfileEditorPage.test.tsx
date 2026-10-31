import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { describe, expect, it } from "vitest";
import { createAdminClient } from "@/features/admin/adminClient";
import { AdminSessionContext } from "@/features/admin/adminSession";
import {
  fakeServer,
  type Handler,
  json,
  TEST_BASE_URL,
} from "@/features/admin/adminTestServer";
import { ProfileEditorPage } from "@/features/admin/ProfileEditorPage";

const PROBLEM = {
  path: "script",
  code: "UNKNOWN_KEY",
  message: "알 수 없는 항목 'script'",
  line: 10,
  column: 1,
};

function renderEditor(path: string, handlers: Record<string, Handler>) {
  const server = fakeServer(handlers);
  const client = createAdminClient({
    baseUrl: TEST_BASE_URL,
    fetch: server.fetch,
    token: () => "token",
  });
  const router = createMemoryRouter(
    [
      { path: "/admin/sources/new", element: <ProfileEditorPage /> },
      { path: "/admin/sources/:id/profile", element: <ProfileEditorPage /> },
      { path: "/admin", element: <p>목록</p> },
    ],
    { initialEntries: [path] },
  );
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <AdminSessionContext.Provider value={client}>
        <RouterProvider router={router} />
      </AdminSessionContext.Provider>
    </QueryClientProvider>,
  );
  return server;
}

describe("ProfileEditorPage", () => {
  it("새 원문의 피드를 미리보고, 검증 문제를 줄:열과 함께 보인다", async () => {
    const user = userEvent.setup();
    const server = renderEditor("/admin/sources/new", {
      "POST /api/admin/profile-previews": () =>
        json({
          result: "INVALID_PROFILE",
          problems: [PROBLEM],
          preview: null,
          shortfalls: [],
          failure: null,
        }),
    });

    await user.type(
      screen.getByLabelText("수집 프로필 원문(YAML)"),
      "script: x",
    );
    const preview = screen.getByRole("button", { name: "미리보기" });
    expect(preview).toBeDisabled();
    await user.type(screen.getByLabelText("미리볼 피드 식별자"), "main");
    await user.click(preview);

    expect(
      await screen.findByText("원문이 검증에 걸려 받지 않았습니다."),
    ).toBeInTheDocument();
    expect(screen.getByText("10:1")).toBeInTheDocument();
    expect(server.requests.map((r) => r.body)).toEqual([
      '{"profile":"script: x","feedKey":"main"}',
    ]);
  });

  it("새 원문을 등록하면 등록한 소스를 알린다", async () => {
    const user = userEvent.setup();
    renderEditor("/admin/sources/new", {
      "POST /api/admin/sources": () =>
        json({ result: "REGISTERED", sourceId: 7, problems: [] }),
    });

    await user.type(screen.getByLabelText("수집 프로필 원문(YAML)"), "source");
    await user.click(screen.getByRole("button", { name: "등록" }));

    expect(await screen.findByRole("status")).toHaveTextContent(
      "소스 7(으)로 등록했습니다.",
    );
  });

  it("등록된 원문을 읽어 고치고, 꺼진 피드 · 소스 · 더한 피드를 알린다", async () => {
    const user = userEvent.setup();
    const server = renderEditor("/admin/sources/5/profile", {
      "GET /api/admin/sources/5/profile": () =>
        json({ profile: "source: 원래\n" }),
      "PUT /api/admin/sources/5/profile": () =>
        json({
          result: "REVISED",
          disabledFeedKeys: ["main"],
          sourceDisabled: true,
          addedFeedKeys: ["added"],
          problems: [],
        }),
    });

    const textarea = await screen.findByLabelText("수집 프로필 원문(YAML)");
    expect(textarea).toHaveValue("source: 원래\n");
    await user.clear(textarea);
    await user.type(textarea, "source: 새");
    await user.click(screen.getByRole("button", { name: "저장" }));

    const status = await screen.findByRole("status");
    expect(status).toHaveTextContent("읽기 설정이 바뀌어 끈 피드: main");
    expect(status).toHaveTextContent("이용조건 검토일이 없어 소스를 껐습니다.");
    expect(status).toHaveTextContent("새로 더한 피드(꺼진 채): added");
    expect(
      server.requests.filter((r) => r.method === "PUT").map((r) => r.body),
    ).toEqual(['{"profile":"source: 새"}']);
  });

  it("끈 피드 · 더한 피드가 없는 수정은 그 줄을 보이지 않는다", async () => {
    const user = userEvent.setup();
    renderEditor("/admin/sources/5/profile", {
      "GET /api/admin/sources/5/profile": () =>
        json({ profile: "source: 원래\n" }),
      "PUT /api/admin/sources/5/profile": () =>
        json({
          result: "REVISED",
          disabledFeedKeys: [],
          sourceDisabled: false,
          addedFeedKeys: [],
          problems: [],
        }),
    });

    await screen.findByLabelText("수집 프로필 원문(YAML)");
    await user.click(screen.getByRole("button", { name: "저장" }));

    const status = await screen.findByRole("status");
    expect(status).toHaveTextContent("저장했습니다.");
    expect(status).not.toHaveTextContent("읽기 설정이 바뀌어 끈 피드");
    expect(status).not.toHaveTextContent("새로 더한 피드");
  });
});
