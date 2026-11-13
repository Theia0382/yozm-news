import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AccountsPage } from "@/features/admin/AccountsPage";
import type { Schemas } from "@/features/admin/adminClient";
import { renderAdminRoutes } from "@/features/admin/reviewTestKit";
import { type Handler, json, problem } from "@/test/fakeServer";

const TOKEN = "q1W2e3R4t5Y6u7I8o9P0aS";

const CLOSED: Schemas["SignUpPolicyStatusResponse"] = {
  open: false,
  changes: [
    {
      previousOpen: null,
      open: false,
      actor: "SYSTEM",
      changedAt: "2026-11-13T00:00:00Z",
    },
  ],
};

function invitation(
  id: number,
  state: Schemas["InvitationRowResponse"]["state"],
  extra: Partial<Schemas["InvitationRowResponse"]> = {},
): Schemas["InvitationRowResponse"] {
  return {
    id,
    memo: null,
    createdAt: "2026-11-13T00:00:00Z",
    expiresAt: "2026-11-20T00:00:00Z",
    state,
    usedAt: null,
    usedByEmail: null,
    revokedAt: null,
    ...extra,
  };
}

function renderPage(handlers: Record<string, Handler> = {}) {
  return renderAdminRoutes(
    [{ path: "/admin/accounts", element: <AccountsPage /> }],
    "/admin/accounts",
    {
      "GET /api/admin/sign-up-policy": () => json(CLOSED),
      "GET /api/admin/invitations": () => json({ invitations: [] }),
      ...handlers,
    },
  );
}

function region(name: string) {
  return within(screen.getByRole("region", { name }));
}

function bodies(
  server: ReturnType<typeof renderPage>["server"],
  method: string,
  path: string,
): unknown[] {
  return server.requests
    .filter((r) => r.method === method && r.path === path)
    .map((r) => (r.body === "" ? null : (JSON.parse(r.body) as unknown)));
}

describe("AccountsPage", () => {
  it("공개 가입의 지금 값과 이력을 보이고, 바꾸면 다시 읽는다", async () => {
    let status = CLOSED;
    const { server } = renderPage({
      "GET /api/admin/sign-up-policy": () => json(status),
      "PUT /api/admin/sign-up-policy": () => {
        status = {
          open: true,
          changes: [
            {
              previousOpen: false,
              open: true,
              actor: "OPERATOR",
              changedAt: "2026-11-13T01:00:00Z",
            },
            ...CLOSED.changes,
          ],
        };
        return json({ result: "CHANGED", previousOpen: false, open: true });
      },
    });
    const user = userEvent.setup();

    expect(await screen.findByText("지금: 닫힘")).toBeInTheDocument();
    expect(
      region("공개 가입").getByText(
        /공개 가입에는 요청 제한 · 봇 차단이 없습니다/,
      ),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole("region", { name: "공개 가입 이력" })).getByText(
        /처음 값 · 닫힘/,
      ),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "공개 가입 열기" }));

    expect(await screen.findByText("지금: 열림")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "공개 가입을 바꿨습니다.",
    );
    expect(
      within(screen.getByRole("region", { name: "공개 가입 이력" })).getByText(
        /운영자 · 닫힘 → 열림/,
      ),
    ).toBeInTheDocument();
    expect(bodies(server, "PUT", "/api/admin/sign-up-policy")).toEqual([
      { open: true },
    ]);
  });

  it("초대 링크를 만들면 이 화면 주소로 만든 링크 전체를 보이고 목록을 다시 읽는다", async () => {
    let rows: Schemas["InvitationRowResponse"][] = [];
    const { server } = renderPage({
      "GET /api/admin/invitations": () => json({ invitations: rows }),
      "POST /api/admin/invitations": () => {
        rows = [invitation(7, "VALID", { memo: "김요즘에게" })];
        return json({
          id: 7,
          token: TOKEN,
          expiresAt: "2026-11-20T00:00:00Z",
        });
      },
    });
    const user = userEvent.setup();
    const invitations = region("초대");

    expect(
      await invitations.findByText("발급한 초대가 없습니다."),
    ).toBeInTheDocument();
    await user.type(
      invitations.getByLabelText("메모 (선택 — 누구에게 보낼 링크인지)"),
      "김요즘에게",
    );
    await user.click(
      invitations.getByRole("button", { name: "초대 링크 만들기" }),
    );

    expect(await invitations.findByLabelText("초대 링크")).toHaveTextContent(
      "http://localhost:3000/invite#q1W2e3R4t5Y6u7I8o9P0aS",
    );
    expect(
      await invitations.findByRole("table", { name: "최근 초대" }),
    ).toHaveTextContent("김요즘에게");
    expect(bodies(server, "POST", "/api/admin/invitations")).toEqual([
      { memo: "김요즘에게" },
    ]);
  });

  it("메모는 입력 그대로 보내고, 메모가 너무 길면 서버의 설명을 보인다", async () => {
    let calls = 0;
    const { server } = renderPage({
      "POST /api/admin/invitations": () =>
        ++calls === 1
          ? json({ id: 1, token: TOKEN, expiresAt: "2026-11-20T00:00:00Z" })
          : problem(400, "메모는 100자까지다"),
    });
    const user = userEvent.setup();
    const invitations = region("초대");
    const button = await invitations.findByRole("button", {
      name: "초대 링크 만들기",
    });

    await user.type(
      invitations.getByLabelText("메모 (선택 — 누구에게 보낼 링크인지)"),
      "   ",
    );
    await user.click(button);
    await invitations.findByLabelText("초대 링크");
    await user.type(
      invitations.getByLabelText("메모 (선택 — 누구에게 보낼 링크인지)"),
      "긴 메모",
    );
    await user.click(button);

    expect(await invitations.findByRole("alert")).toHaveTextContent(
      "메모는 100자까지다",
    );
    expect(bodies(server, "POST", "/api/admin/invitations")).toEqual([
      { memo: "   " },
      { memo: "긴 메모" },
    ]);
  });

  it("목록은 상태 · 가입한 계정을 보이고, 유효한 초대만 취소할 수 있다", async () => {
    let rows = [
      invitation(5, "VALID"),
      invitation(4, "USED", {
        usedAt: "2026-11-14T00:00:00Z",
        usedByEmail: "kim@example.com",
      }),
      invitation(3, "EXPIRED"),
      invitation(2, "REVOKED", { revokedAt: "2026-11-13T02:00:00Z" }),
    ];
    const { server } = renderPage({
      "GET /api/admin/invitations": () => json({ invitations: rows }),
      "POST /api/admin/invitations/5/revoke": () => {
        rows = [invitation(5, "REVOKED"), ...rows.slice(1)];
        return json({ result: "REVOKED", state: null });
      },
    });
    const user = userEvent.setup();

    const table = await screen.findByRole("table", { name: "최근 초대" });
    const [, valid, used, expired, revoked] = within(table).getAllByRole("row");
    expect(valid).toHaveTextContent("유효");
    expect(used).toHaveTextContent("사용됨");
    expect(used).toHaveTextContent("kim@example.com");
    expect(expired).toHaveTextContent("만료");
    expect(revoked).toHaveTextContent("취소");
    expect(within(table).getAllByRole("button", { name: "취소" })).toHaveLength(
      1,
    );

    await user.click(within(valid!).getByRole("button", { name: "취소" }));

    expect(await region("초대").findByRole("status")).toHaveTextContent(
      "초대를 취소했습니다.",
    );
    expect(
      within(screen.getByRole("table", { name: "최근 초대" })).queryAllByRole(
        "button",
        { name: "취소" },
      ),
    ).toHaveLength(0);
    expect(
      server.requests.filter(
        (r) => r.method === "POST" && r.path.endsWith("/revoke"),
      ),
    ).toHaveLength(1);
  });

  it("취소하는 사이 쓰인 초대는 취소하지 않았다고 알린다", async () => {
    renderPage({
      "GET /api/admin/invitations": () =>
        json({ invitations: [invitation(5, "VALID")] }),
      "POST /api/admin/invitations/5/revoke": () =>
        json({ result: "NOT_REVOCABLE", state: "USED" }),
    });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "취소" }));

    expect(await region("초대").findByRole("alert")).toHaveTextContent(
      "유효한 초대가 아니라 취소하지 않았습니다.",
    );
  });

  it("계정을 만들거나 이미 있는 이메일이라 만들지 않았다고 알리고, 규칙 위반은 서버의 설명을 보인다", async () => {
    let calls = 0;
    const { server } = renderPage({
      "POST /api/admin/accounts": () => {
        calls += 1;
        if (calls === 1) {
          return json({ result: "CREATED", userId: 42 });
        }
        if (calls === 2) {
          return json({ result: "EMAIL_TAKEN", userId: null });
        }
        return problem(400, "초기 비밀번호가 짧다 — 8 ~ 128자다");
      },
    });
    const user = userEvent.setup();
    const creation = region("계정 만들기");
    const submit = async () =>
      user.click(creation.getByRole("button", { name: "계정 만들기" }));

    await user.type(
      await creation.findByLabelText("이메일"),
      "kim@example.com",
    );
    await user.type(creation.getByLabelText("표시 이름"), "김요즘");
    await user.type(
      creation.getByLabelText("초기 비밀번호"),
      "initial horse battery",
    );
    await submit();
    expect(await creation.findByRole("status")).toHaveTextContent(
      "계정을 만들었습니다. 첫 로그인에서 비밀번호를 바꿔야 합니다.",
    );
    expect(creation.getByLabelText("초기 비밀번호")).toHaveValue("");

    await user.type(
      creation.getByLabelText("초기 비밀번호"),
      "initial horse battery",
    );
    await submit();
    expect(await creation.findByRole("alert")).toHaveTextContent(
      "이미 있는 이메일이라 만들지 않았습니다.",
    );

    await submit();
    expect(
      await creation.findByText("초기 비밀번호가 짧다 — 8 ~ 128자다"),
    ).toBeInTheDocument();
    expect(bodies(server, "POST", "/api/admin/accounts")[0]).toEqual({
      email: "kim@example.com",
      displayName: "김요즘",
      initialPassword: "initial horse battery",
    });
  });

  it("초기 비밀번호를 다시 정하거나, 바꾼 계정 · 없는 계정이면 그렇다고 알린다", async () => {
    let calls = 0;
    const { server } = renderPage({
      "PUT /api/admin/accounts/initial-password": () => {
        calls += 1;
        if (calls === 1) {
          return json({ result: "RESET" });
        }
        if (calls === 2) {
          return json({ result: "NOT_PENDING" });
        }
        return problem(404, "그 이메일의 계정이 없다");
      },
    });
    const user = userEvent.setup();
    const reset = region("초기 비밀번호 다시 정하기");
    const fill = async () => {
      await user.clear(reset.getByLabelText("새 초기 비밀번호"));
      await user.type(
        reset.getByLabelText("새 초기 비밀번호"),
        "another initial horse",
      );
      await user.click(reset.getByRole("button", { name: "다시 정하기" }));
    };

    await user.type(await reset.findByLabelText("이메일"), "park@example.com");
    await fill();
    expect(await reset.findByRole("status")).toHaveTextContent(
      "초기 비밀번호를 다시 정했습니다. 그 계정의 로그인은 모두 끝났습니다.",
    );

    await fill();
    expect(await reset.findByRole("alert")).toHaveTextContent(
      "이미 비밀번호를 바꾼 계정이라 바꾸지 않았습니다.",
    );

    await fill();
    expect(
      await reset.findByText("그 이메일의 계정이 없다"),
    ).toBeInTheDocument();
    expect(
      bodies(server, "PUT", "/api/admin/accounts/initial-password")[0],
    ).toEqual({
      email: "park@example.com",
      initialPassword: "another initial horse",
    });
  });
});
