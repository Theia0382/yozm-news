import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { InvitePage } from "@/features/account/InvitePage";
import { json, type Handler } from "@/test/fakeServer";
import {
  ANONYMOUS_ME,
  bodiesOf,
  renderRoutes,
  SIGNED_IN_ME,
} from "@/test/renderRoutes";

const TOKEN = "q1W2e3R4t5Y6u7I8o9P0aS";

const CODE = "ABCD-EFGH-JKMN-PQRS-TVWX-YZ01-2345-6789";

function renderInvite(entry: string, state: string, accept?: Handler) {
  let signedUp = false;
  return renderRoutes(
    [
      { path: "/invite", element: <InvitePage /> },
      { path: "/", element: <p>첫 화면</p> },
    ],
    entry,
    {
      "GET /api/me": () => json(signedUp ? SIGNED_IN_ME : ANONYMOUS_ME),
      "POST /api/invitation-checks": () => json({ state }),
      "POST /api/invitation-acceptances": async (request) => {
        const response = await (accept ?? (() => json({})))(request);
        const body = (await response.clone().json()) as { result?: string };
        signedUp = body.result === "SIGNED_UP";
        return response;
      },
    },
  );
}

async function fill() {
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText("이메일"), "kim@example.com");
  await user.type(screen.getByLabelText("표시 이름"), "김요즘");
  await user.type(screen.getByLabelText("비밀번호"), "correct horse");
  await user.type(screen.getByLabelText("비밀번호 확인"), "correct horse");
  await user.click(screen.getByRole("button", { name: "가입" }));
  return user;
}

describe("InvitePage", () => {
  it("주소에 토큰이 없으면 묻지 않고 없는 링크라고 안내한다", async () => {
    const { server } = renderInvite("/invite", "VALID");

    expect(await screen.findByText(/없는 초대 링크입니다/)).toBeInTheDocument();
    expect(bodiesOf(server, "POST", "/api/invitation-checks")).toEqual([]);
  });

  it("쓴 초대면 입력 없이 그 까닭을 보인다", async () => {
    const { server } = renderInvite(`/invite#${TOKEN}`, "USED");

    expect(
      await screen.findByText("이미 쓴 초대 링크입니다."),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("이메일")).not.toBeInTheDocument();
    expect(bodiesOf(server, "POST", "/api/invitation-checks")).toEqual([
      { token: TOKEN },
    ]);
  });

  it("쓸 수 있는 초대면 토큰을 실어 가입하고 복구 코드를 보인 뒤 첫 화면으로 간다", async () => {
    const { server } = renderInvite(`/invite#${TOKEN}`, "VALID", () =>
      json({
        result: "SIGNED_UP",
        recoveryCode: CODE,
        passwordProblem: null,
        invitation: null,
      }),
    );

    const user = await fill();

    expect(await screen.findByLabelText("복구 코드 값")).toHaveTextContent(
      CODE,
    );
    expect(bodiesOf(server, "POST", "/api/invitation-acceptances")).toEqual([
      {
        token: TOKEN,
        email: "kim@example.com",
        displayName: "김요즘",
        password: "correct horse",
        keep: false,
      },
    ]);
    await user.click(screen.getByLabelText("복구 코드를 저장했습니다"));
    await user.click(screen.getByRole("button", { name: "계속" }));
    expect(await screen.findByText("첫 화면")).toBeInTheDocument();
  });

  it("가입하는 사이 초대가 취소되면 그 까닭을 보인다", async () => {
    renderInvite(`/invite#${TOKEN}`, "VALID", () =>
      json({
        result: "INVITATION_UNUSABLE",
        recoveryCode: null,
        passwordProblem: null,
        invitation: "REVOKED",
      }),
    );

    await fill();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "취소된 초대 링크입니다. 운영자에게 새 링크를 받으세요.",
    );
  });
});
