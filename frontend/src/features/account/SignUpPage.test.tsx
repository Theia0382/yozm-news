import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { SignUpPage } from "@/features/account/SignUpPage";
import { json, type Handler } from "@/test/fakeServer";
import {
  ANONYMOUS_ME,
  bodiesOf,
  renderRoutes,
  SIGNED_IN_ME,
} from "@/test/renderRoutes";

const CODE = "ABCD-EFGH-JKMN-PQRS-TVWX-YZ01-2345-6789";

function renderSignUp(open: boolean, signUp: Handler) {
  let signedUp = false;
  return renderRoutes(
    [
      { path: "/sign-up", element: <SignUpPage /> },
      { path: "/", element: <p>첫 화면</p> },
      { path: "/login", element: <p>로그인 화면</p> },
    ],
    "/sign-up",
    {
      "GET /api/me": () => json(signedUp ? SIGNED_IN_ME : ANONYMOUS_ME),
      "GET /api/sign-up-policy": () => json({ open }),
      "POST /api/sign-ups": async (request) => {
        const response = await signUp(request);
        const body = (await response.clone().json()) as { result?: string };
        signedUp = body.result === "SIGNED_UP";
        return response;
      },
    },
  );
}

function result(
  result: string,
  extra: Record<string, unknown> = {},
): () => Response {
  return () =>
    json({
      result,
      recoveryCode: null,
      passwordProblem: null,
      invitation: null,
      ...extra,
    });
}

async function fill(password = "correct horse", confirm = password) {
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText("이메일"), "kim@example.com");
  await user.type(screen.getByLabelText("표시 이름"), "김요즘");
  await user.type(screen.getByLabelText("비밀번호"), password);
  await user.type(screen.getByLabelText("비밀번호 확인"), confirm);
  await user.click(screen.getByLabelText("로그인 유지"));
  await user.click(screen.getByRole("button", { name: "가입" }));
  return user;
}

describe("SignUpPage", () => {
  it("가입 양식에 고지 페이지로 가는 링크를 둔다", async () => {
    renderSignUp(true, result("SIGNED_UP"));

    expect(
      await screen.findByRole("link", {
        name: "저장하는 것과 LLM 공급자에게 보내는 것",
      }),
    ).toHaveAttribute("href", "/privacy");
  });

  it("공개 가입이 닫혔으면 입력 없이 초대를 받으라고 안내한다", async () => {
    renderSignUp(false, result("CLOSED"));

    expect(
      await screen.findByText(/지금은 가입을 받지 않습니다/),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("이메일")).not.toBeInTheDocument();
  });

  it("가입하면 복구 코드를 보이고, 저장을 확인하면 첫 화면으로 간다", async () => {
    const { server, router } = renderSignUp(
      true,
      result("SIGNED_UP", { recoveryCode: CODE }),
    );

    const user = await fill();

    expect(await screen.findByText(CODE)).toBeInTheDocument();
    expect(bodiesOf(server, "POST", "/api/sign-ups")).toEqual([
      {
        email: "kim@example.com",
        displayName: "김요즘",
        password: "correct horse",
        keep: true,
      },
    ]);
    await user.click(screen.getByLabelText("복구 코드를 저장했습니다"));
    await user.click(screen.getByRole("button", { name: "계속" }));

    expect(await screen.findByText("첫 화면")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/");
  });

  it("같은 이메일의 계정이 있으면 가입할 수 없는 이메일이라고만 한다", async () => {
    renderSignUp(true, result("EMAIL_UNAVAILABLE"));

    await fill();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "가입할 수 없는 이메일입니다.",
    );
  });

  it("비밀번호 규칙을 어기면 그 까닭을 보인다", async () => {
    renderSignUp(
      true,
      result("PASSWORD_REJECTED", { passwordProblem: "TOO_SHORT" }),
    );

    await fill("short", "short");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "비밀번호가 너무 짧습니다. 더 길게 정해 주세요.",
    );
  });

  it("비밀번호와 확인이 다르면 보내지 않는다", async () => {
    const { server } = renderSignUp(true, result("SIGNED_UP"));

    await fill("correct horse", "correct hose");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "비밀번호와 확인이 서로 다릅니다.",
    );
    expect(bodiesOf(server, "POST", "/api/sign-ups")).toEqual([]);
  });
});
