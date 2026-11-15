import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { InvitePage } from "@/features/account/InvitePage";
import { json, type Handler } from "@/test/fakeServer";
import {
  installFakeTurnstile,
  uninstallFakeTurnstile,
} from "@/test/fakeTurnstile";
import {
  ANONYMOUS_ME,
  bodiesOf,
  renderRoutes,
  SIGNED_IN_ME,
} from "@/test/renderRoutes";

const TOKEN = "q1W2e3R4t5Y6u7I8o9P0aS";

const CODE = "ABCD-EFGH-JKMN-PQRS-TVWX-YZ01-2345-6789";

function renderInvite(
  entry: string,
  state: string,
  accept?: Handler,
  extra: Record<string, Handler> = {},
) {
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
      ...extra,
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

  it("쓸 수 있는 초대의 가입 양식에 고지 페이지로 가는 링크를 둔다", async () => {
    renderInvite(`/invite#${TOKEN}`, "VALID", () => json({}));

    expect(
      await screen.findByRole("link", {
        name: "저장하는 것과 LLM 공급자에게 보내는 것",
      }),
    ).toHaveAttribute("href", "/privacy");
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

    expect(await screen.findByText(CODE)).toBeInTheDocument();
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

describe("InvitePage 봇 차단 · 요청 제한", () => {
  afterEach(() => uninstallFakeTurnstile());

  it("봇 차단을 켠 인스턴스여도 초대 가입에는 위젯이 없고 토큰 없이 보낸다", async () => {
    const turnstile = installFakeTurnstile();
    const { server } = renderInvite(
      "/invite#invite-token",
      "VALID",
      () =>
        json({
          result: "EMAIL_UNAVAILABLE",
          recoveryCode: null,
          passwordProblem: null,
          invitation: null,
        }),
      {
        "GET /api/human-check": () =>
          json({ provider: "TURNSTILE", siteKey: "1x00000000000000000000AA" }),
      },
    );

    await fill();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "가입할 수 없는 이메일입니다.",
    );
    expect(turnstile.rendered).toEqual([]);
    const acceptance = server.requests.find(
      (r) => r.path === "/api/invitation-acceptances",
    );
    expect(acceptance?.headers.get("X-Human-Check")).toBeNull();
  });

  it("초대 확인이 요청 제한에 걸리면 요청 제한 안내를 보인다", async () => {
    renderInvite("/invite#invite-token", "VALID", undefined, {
      "POST /api/invitation-checks": () =>
        new Response(
          JSON.stringify({ type: "urn:yozmnews:problem:rate-limited" }),
          {
            status: 429,
            headers: {
              "Content-Type": "application/problem+json",
              "Retry-After": "30",
            },
          },
        ),
    });

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "이 네트워크에서 요청이 너무 많아 잠시 막혔습니다. 30초 뒤에 다시 시도해 주세요.",
    );
  });

  it("서버에 닿지 못하면 브라우저의 오류 문구가 아니라 고정 안내를 보인다", async () => {
    renderInvite("/invite#invite-token", "VALID", undefined, {
      "POST /api/invitation-checks": () => {
        throw new TypeError("Failed to fetch");
      },
    });

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("초대를 확인하지 못했습니다.");
    expect(alert).not.toHaveTextContent("Failed to fetch");
  });
});
