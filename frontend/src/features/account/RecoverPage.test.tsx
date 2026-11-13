import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { RecoverPage } from "@/features/account/RecoverPage";
import { json, type Handler } from "@/test/fakeServer";
import {
  ANONYMOUS_ME,
  bodiesOf,
  renderRoutes,
  SIGNED_IN_ME,
} from "@/test/renderRoutes";

const OLD_CODE = "ABCD-EFGH-JKMN-PQRS-TVWX-YZ01-2345-6789";

const NEW_CODE = "ZZZZ-YYYY-XXXX-WWWW-VVVV-TTTT-SSSS-RRRR";

function renderRecover(recover: Handler) {
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

    expect(await screen.findByLabelText("복구 코드 값")).toHaveTextContent(
      NEW_CODE,
    );
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
