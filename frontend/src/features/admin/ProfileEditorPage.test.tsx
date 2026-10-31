import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
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
  return Object.assign(server, { router });
}

const PREVIEW_INVALID = {
  result: "INVALID_PROFILE",
  problems: [PROBLEM],
  preview: null,
  shortfalls: [],
  failure: null,
};

/** 응답을 시험이 정할 때 보내는 처리기 */
function held() {
  let answer: (response: Response) => void = () => {};
  const handler: Handler = () =>
    new Promise<Response>((resolve) => {
      answer = resolve;
    });
  return { handler, answer: (response: Response) => answer(response) };
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

  it("새 원문을 등록하면 그 소스의 편집 화면으로 옮겨 등록을 알린다", async () => {
    const user = userEvent.setup();
    const server = renderEditor("/admin/sources/new", {
      "POST /api/admin/sources": () =>
        json({ result: "REGISTERED", sourceId: 7, problems: [] }),
      "GET /api/admin/sources/7/profile": () => json({ profile: "source" }),
    });

    await user.type(screen.getByLabelText("수집 프로필 원문(YAML)"), "source");
    await user.click(screen.getByRole("button", { name: "등록" }));

    // 안내에 소스 ID 가 없다 — 정확 일치
    expect(await screen.findByRole("status")).toHaveTextContent(
      /^등록했습니다\. 소스와 피드는 꺼진 채이니 목록에서 켜 주세요\.$/,
    );
    expect(server.router.state.location.pathname).toBe(
      "/admin/sources/7/profile",
    );
    expect(
      screen.getByRole("heading", { name: "운영자 프로필 원문 편집" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "저장" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "등록" })).toBeNull();
    // 안내를 실은 라우터 상태는 읽은 뒤 지운다 — 새로고침 · 뒤로 가기에서 다시 나오지 않게
    await waitFor(() => expect(server.router.state.location.state).toBeNull());
    expect(screen.getByRole("status")).toHaveTextContent("등록했습니다.");
  });

  it("원문이 검증에 걸려 등록하지 못하면 새 원문 화면에 머문다", async () => {
    const user = userEvent.setup();
    const server = renderEditor("/admin/sources/new", {
      "POST /api/admin/sources": () =>
        json({
          result: "INVALID_PROFILE",
          sourceId: null,
          problems: [PROBLEM],
        }),
    });

    await user.type(screen.getByLabelText("수집 프로필 원문(YAML)"), "script");
    await user.click(screen.getByRole("button", { name: "등록" }));

    expect(
      await screen.findByText("원문이 검증에 걸려 등록하지 않았습니다."),
    ).toBeInTheDocument();
    expect(server.router.state.location.pathname).toBe("/admin/sources/new");
    expect(screen.getByRole("button", { name: "등록" })).toBeInTheDocument();
  });

  it("원문이나 미리볼 피드 식별자를 고치면 그전 미리보기 결과를 지운다", async () => {
    const user = userEvent.setup();
    renderEditor("/admin/sources/new", {
      "POST /api/admin/profile-previews": () => json(PREVIEW_INVALID),
    });
    const textarea = screen.getByLabelText("수집 프로필 원문(YAML)");
    const feedKey = screen.getByLabelText("미리볼 피드 식별자");
    const preview = screen.getByRole("button", { name: "미리보기" });

    await user.type(textarea, "script: x");
    await user.type(feedKey, "main");
    await user.click(preview);
    expect(
      await screen.findByText("원문이 검증에 걸려 받지 않았습니다."),
    ).toBeInTheDocument();
    await user.type(textarea, "y");
    expect(
      screen.queryByText("원문이 검증에 걸려 받지 않았습니다."),
    ).toBeNull();

    await user.click(preview);
    expect(
      await screen.findByText("원문이 검증에 걸려 받지 않았습니다."),
    ).toBeInTheDocument();
    await user.type(feedKey, "2");
    expect(
      screen.queryByText("원문이 검증에 걸려 받지 않았습니다."),
    ).toBeNull();
  });

  it("미리보는 동안 원문을 고치면 늦게 온 결과를 보이지 않는다", async () => {
    const user = userEvent.setup();
    const first = held();
    const second = held();
    let calls = 0;
    renderEditor("/admin/sources/new", {
      "POST /api/admin/profile-previews": (request) =>
        ++calls === 1 ? first.handler(request) : second.handler(request),
    });
    const textarea = screen.getByLabelText("수집 프로필 원문(YAML)");
    await user.type(textarea, "script: x");
    await user.type(screen.getByLabelText("미리볼 피드 식별자"), "main");

    await user.click(screen.getByRole("button", { name: "미리보기" }));
    expect(
      screen.getByRole("button", { name: "받아서 확인하는 중…" }),
    ).toBeDisabled();
    await user.type(textarea, "y");
    first.answer(json(PREVIEW_INVALID));
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(
      screen.queryByText("원문이 검증에 걸려 받지 않았습니다."),
    ).toBeNull();

    // 고치지 않고 기다리면 결과가 보인다
    await user.click(screen.getByRole("button", { name: "미리보기" }));
    second.answer(json(PREVIEW_INVALID));
    expect(
      await screen.findByText("원문이 검증에 걸려 받지 않았습니다."),
    ).toBeInTheDocument();
  });

  it("등록하는 동안 원문과 미리볼 피드 식별자를 잠근다", async () => {
    const user = userEvent.setup();
    const registration = held();
    const server = renderEditor("/admin/sources/new", {
      "POST /api/admin/sources": registration.handler,
      "GET /api/admin/sources/7/profile": () => json({ profile: "source" }),
    });
    const textarea = screen.getByLabelText("수집 프로필 원문(YAML)");
    const feedKey = screen.getByLabelText("미리볼 피드 식별자");
    await user.type(textarea, "source");

    await user.click(screen.getByRole("button", { name: "등록" }));

    expect(textarea).toHaveAttribute("readonly");
    expect(feedKey).toHaveAttribute("readonly");
    await user.type(textarea, "x");
    expect(textarea).toHaveValue("source");
    expect(screen.getByRole("button", { name: "등록" })).toBeDisabled();

    registration.answer(
      json({ result: "REGISTERED", sourceId: 7, problems: [] }),
    );
    const moved = await screen.findByRole("button", { name: "저장" });
    expect(moved).toBeEnabled();
    expect(screen.getByLabelText("수집 프로필 원문(YAML)")).not.toHaveAttribute(
      "readonly",
    );
    expect(
      server.requests.filter(
        (r) => r.method === "POST" && r.path === "/api/admin/sources",
      ),
    ).toHaveLength(1);
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
