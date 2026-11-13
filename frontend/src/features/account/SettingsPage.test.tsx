import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { SettingsPage } from "@/features/account/SettingsPage";
import { json, type Handler } from "@/test/fakeServer";
import { bodiesOf, renderRoutes, SIGNED_IN_ME } from "@/test/renderRoutes";

const CODE = "ABCD-EFGH-JKMN-PQRS-TVWX-YZ01-2345-6789";

function renderSettings(handlers: Record<string, Handler>) {
  return renderRoutes(
    [{ path: "/settings", element: <SettingsPage /> }],
    "/settings",
    { "GET /api/me": () => json(SIGNED_IN_ME), ...handlers },
  );
}

function section(name: string) {
  return within(screen.getByRole("region", { name }));
}

describe("SettingsPage", () => {
  it("비밀번호를 바꾸면 다른 기기의 로그인이 끝났다고 알린다", async () => {
    const { server } = renderSettings({
      "PUT /api/account/password": () =>
        json({
          result: "CHANGED",
          recoveryCode: null,
          passwordProblem: null,
          retryAfterSeconds: null,
        }),
    });
    const user = userEvent.setup();
    const change = within(
      await screen.findByRole("region", { name: "비밀번호 변경" }),
    );

    await user.type(
      change.getByLabelText("지금 비밀번호"),
      "old horse battery",
    );
    await user.type(change.getByLabelText("새 비밀번호"), "new horse battery");
    await user.type(
      change.getByLabelText("새 비밀번호 확인"),
      "new horse battery",
    );
    await user.click(change.getByRole("button", { name: "비밀번호 바꾸기" }));

    expect(await change.findByRole("status")).toHaveTextContent(
      "비밀번호를 바꿨습니다. 다른 기기의 로그인은 모두 끝났습니다.",
    );
    expect(bodiesOf(server, "PUT", "/api/account/password")).toEqual([
      {
        currentPassword: "old horse battery",
        newPassword: "new horse battery",
      },
    ]);
  });

  it("복구 코드를 새로 받으면 코드를 보이고, 저장을 확인하면 설정으로 돌아와 알린다", async () => {
    const { server } = renderSettings({
      "POST /api/account/recovery-code": () =>
        json({ result: "ISSUED", recoveryCode: CODE, retryAfterSeconds: null }),
    });
    const user = userEvent.setup();
    const reissue = within(
      await screen.findByRole("region", { name: "복구 코드 새로 받기" }),
    );

    await user.type(
      reissue.getByLabelText("지금 비밀번호"),
      "my horse battery",
    );
    await user.click(
      reissue.getByRole("button", { name: "복구 코드 새로 받기" }),
    );

    expect(await screen.findByLabelText("복구 코드 값")).toHaveTextContent(
      CODE,
    );
    expect(bodiesOf(server, "POST", "/api/account/recovery-code")).toEqual([
      { currentPassword: "my horse battery" },
    ]);
    await user.click(screen.getByLabelText("복구 코드를 저장했습니다"));
    await user.click(screen.getByRole("button", { name: "계속" }));

    expect(
      await section("복구 코드 새로 받기").findByRole("status"),
    ).toHaveTextContent(
      "새 복구 코드를 받았습니다. 이전 코드는 더 쓸 수 없습니다.",
    );
  });

  it("지금 비밀번호가 틀리거나 시도가 많으면 알린다", async () => {
    let calls = 0;
    renderSettings({
      "POST /api/account/recovery-code": () =>
        ++calls === 1
          ? json({
              result: "REJECTED",
              recoveryCode: null,
              retryAfterSeconds: null,
            })
          : json({ result: "WAIT", recoveryCode: null, retryAfterSeconds: 30 }),
    });
    const user = userEvent.setup();
    const reissue = within(
      await screen.findByRole("region", { name: "복구 코드 새로 받기" }),
    );

    await user.type(reissue.getByLabelText("지금 비밀번호"), "wrong");
    await user.click(
      reissue.getByRole("button", { name: "복구 코드 새로 받기" }),
    );
    expect(await reissue.findByRole("alert")).toHaveTextContent(
      "지금 비밀번호가 맞지 않습니다.",
    );

    await user.type(reissue.getByLabelText("지금 비밀번호"), "wrong");
    await user.click(
      reissue.getByRole("button", { name: "복구 코드 새로 받기" }),
    );
    expect(await reissue.findByText(/30초 뒤에/)).toHaveTextContent(
      "비밀번호 확인 시도가 많아 잠시 막혔습니다. 30초 뒤에 다시 시도해 주세요.",
    );
  });
});
