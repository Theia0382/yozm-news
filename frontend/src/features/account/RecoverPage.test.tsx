import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { RecoverPage } from "@/features/account/RecoverPage";
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

const OLD_CODE = "ABCD-EFGH-JKMN-PQRS-TVWX-YZ01-2345-6789";

const NEW_CODE = "ZZZZ-YYYY-XXXX-WWWW-VVVV-TTTT-SSSS-RRRR";

function renderRecover(recover: Handler, extra: Record<string, Handler> = {}) {
  let reset = false;
  return renderRoutes(
    [
      { path: "/recover", element: <RecoverPage /> },
      { path: "/", element: <p>첫 화면</p> },
    ],
    "/recover",
    {
      "GET /api/me": () => json(reset ? SIGNED_IN_ME : ANONYMOUS_ME),
      "POST /api/account-recoveries": async (request) => {
        const response = await recover(request);
        const body = (await response.clone().json()) as { result?: string };
        reset = body.result === "RESET";
        return response;
      },
      ...extra,
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
      retryAfterSeconds: null,
      ...extra,
    });
}

async function fill() {
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText("이메일"), "kim@example.com");
  await user.type(screen.getByLabelText("복구 코드"), OLD_CODE);
  await user.type(screen.getByLabelText("새 비밀번호"), "new horse battery");
  await user.type(
    screen.getByLabelText("새 비밀번호 확인"),
    "new horse battery",
  );
  await user.click(screen.getByRole("button", { name: "재설정" }));
  return user;
}

describe("RecoverPage", () => {
  it("재설정하면 새 복구 코드를 보이고 저장을 확인하면 첫 화면으로 간다", async () => {
    const { server } = renderRecover(
      result("RESET", { recoveryCode: NEW_CODE }),
    );

    const user = await fill();

    expect(await screen.findByText(NEW_CODE)).toBeInTheDocument();
    expect(bodiesOf(server, "POST", "/api/account-recoveries")).toEqual([
      {
        email: "kim@example.com",
        recoveryCode: OLD_CODE,
        newPassword: "new horse battery",
        keep: false,
      },
    ]);
    await user.click(screen.getByLabelText("복구 코드를 저장했습니다"));
    await user.click(screen.getByRole("button", { name: "계속" }));
    expect(await screen.findByText("첫 화면")).toBeInTheDocument();
  });

  it("거절은 계정 유무와 무관하게 같은 문구다", async () => {
    renderRecover(result("REJECTED"));

    expect(
      await screen.findByText(
        /복구 코드가 없으면 비밀번호를 되찾을 수 없습니다/,
      ),
    ).toBeInTheDocument();

    await fill();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "이메일 또는 복구 코드가 맞지 않습니다.",
    );
  });

  it("시도가 많으면 기다릴 시간을 보인다", async () => {
    renderRecover(result("WAIT", { retryAfterSeconds: 120 }));

    await fill();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "재설정 시도가 많아 잠시 막혔습니다. 2분 뒤에 다시 시도해 주세요.",
    );
  });
});

describe("RecoverPage 봇 차단 · 요청 제한", () => {
  afterEach(() => uninstallFakeTurnstile());

  it("켠 인스턴스는 재설정 위젯의 토큰을 헤더로 싣는다", async () => {
    const turnstile = installFakeTurnstile();
    const { server } = renderRecover(result("REJECTED"), {
      "GET /api/human-check": () =>
        json({ provider: "TURNSTILE", siteKey: "1x00000000000000000000AA" }),
    });

    await screen.findByText("사람 확인을 마치면 제출할 수 있습니다.");
    expect(turnstile.rendered[0]?.action).toBe("account-recovery");
    turnstile.solve("0.recovery-token");
    await fill();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "이메일 또는 복구 코드가 맞지 않습니다.",
    );
    const recovery = server.requests.find(
      (r) => r.path === "/api/account-recoveries",
    );
    expect(recovery?.headers.get("X-Human-Check")).toBe("0.recovery-token");
  });

  it("요청 제한에 걸리면 요청 제한 안내를 보인다", async () => {
    renderRecover(
      () =>
        new Response(
          JSON.stringify({ type: "urn:yozmnews:problem:rate-limited" }),
          {
            status: 429,
            headers: {
              "Content-Type": "application/problem+json",
              "Retry-After": "3600",
            },
          },
        ),
    );

    await fill();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "이 네트워크에서 요청이 너무 많아 잠시 막혔습니다. 60분 뒤에 다시 시도해 주세요.",
    );
  });
});
