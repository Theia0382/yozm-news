import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { PasswordPage } from "@/features/account/PasswordPage";
import { RequireSignIn } from "@/features/session/RequireSignIn";
import { json, type Handler } from "@/test/fakeServer";
import { bodiesOf, renderRoutes, SIGNED_IN_ME } from "@/test/renderRoutes";

const CODE = "ABCD-EFGH-JKMN-PQRS-TVWX-YZ01-2345-6789";

function renderGuarded(entry: string, forced: boolean, change?: Handler) {
  let mustChange = forced;
  return renderRoutes(
    [
      {
        element: <RequireSignIn />,
        children: [
          { path: "/", element: <p>첫 화면</p> },
          { path: "/notices", element: <p>알림 화면</p> },
          { path: "/password", element: <PasswordPage /> },
          { path: "/settings", element: <p>설정 화면</p> },
        ],
      },
    ],
    entry,
    {
      "GET /api/me": () =>
        json({ ...SIGNED_IN_ME, passwordChangeRequired: mustChange }),
      "GET /api/notices/unread-count": () => json({ count: 0 }),
      "PUT /api/account/password": async (request) => {
        const response = await (change ?? (() => json({})))(request);
        const body = (await response.clone().json()) as { result?: string };
        mustChange = mustChange && body.result !== "CHANGED";
        return response;
      },
    },
  );
}

async function fill(newPassword = "my own horse battery") {
  const user = userEvent.setup();
  await user.type(
    await screen.findByLabelText("지금 비밀번호"),
    "initial horse battery",
  );
  await user.type(screen.getByLabelText("새 비밀번호"), newPassword);
  await user.type(screen.getByLabelText("새 비밀번호 확인"), newPassword);
  await user.click(screen.getByRole("button", { name: "비밀번호 바꾸기" }));
  return user;
}

describe("PasswordPage", () => {
  it("첫 로그인의 변경이 남았으면 다른 주소로 와도 변경 화면이고, 머리줄에 알림 · 설정 링크가 없다", async () => {
    const { router, server } = renderGuarded("/notices", true);

    expect(
      await screen.findByText(/운영자가 정해 준 비밀번호를 쓰고 있습니다/),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/password");
    expect(screen.queryByText("알림 화면")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /알림/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "설정" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "로그아웃" }),
    ).toBeInTheDocument();
    expect(
      server.requests.some((r) => r.path === "/api/notices/unread-count"),
    ).toBe(false);
  });

  it("바꾸면 복구 코드를 보이고, 저장을 확인하면 첫 화면으로 간다", async () => {
    const { server, router } = renderGuarded("/password", true, () =>
      json({
        result: "CHANGED",
        recoveryCode: CODE,
        passwordProblem: null,
        retryAfterSeconds: null,
      }),
    );

    const user = await fill();

    expect(await screen.findByText(CODE)).toBeInTheDocument();
    expect(bodiesOf(server, "PUT", "/api/account/password")).toEqual([
      {
        currentPassword: "initial horse battery",
        newPassword: "my own horse battery",
      },
    ]);
    await user.click(screen.getByLabelText("복구 코드를 저장했습니다"));
    await user.click(screen.getByRole("button", { name: "계속" }));

    expect(await screen.findByText("첫 화면")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/");
    expect(screen.getByRole("link", { name: "설정" })).toBeInTheDocument();
  });

  it("지금 비밀번호가 틀리면 그대로 남아 알린다", async () => {
    renderGuarded("/password", true, () =>
      json({
        result: "REJECTED",
        recoveryCode: null,
        passwordProblem: null,
        retryAfterSeconds: null,
      }),
    );

    await fill();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "지금 비밀번호가 맞지 않습니다.",
    );
  });

  it("변경이 남지 않은 계정은 설정으로 보낸다", async () => {
    const { router } = renderGuarded("/password", false);

    expect(await screen.findByText("설정 화면")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/settings");
  });
});
