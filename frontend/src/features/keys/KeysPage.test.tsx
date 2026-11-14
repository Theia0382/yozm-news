import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { KeysPage } from "@/features/keys/KeysPage";
import { RequireSignIn } from "@/features/session/RequireSignIn";
import type { components } from "@/shared/api/schema";
import { type Handler, json, noContent, problem } from "@/test/fakeServer";
import { bodiesOf, renderRoutes, SIGNED_IN_ME } from "@/test/renderRoutes";

type LlmKeys = components["schemas"]["LlmKeysResponse"];
type LlmKey = NonNullable<components["schemas"]["ProviderKeyResponse"]["key"]>;

const NO_KEYS: LlmKeys = {
  providers: [
    { provider: "OPENAI", key: null },
    { provider: "ANTHROPIC", key: null },
  ],
};

const STORED: LlmKey = {
  hint: "WXYZ",
  status: "VALID",
  validatedAt: "2026-11-14T06:00:00Z",
  createdAt: "2026-11-14T06:00:00Z",
};

function withAnthropic(key: LlmKey): LlmKeys {
  return {
    providers: [
      { provider: "OPENAI", key: null },
      { provider: "ANTHROPIC", key },
    ],
  };
}

/** 로그인 틀 안에 그린다 — 키가 바뀌면 틀이 읽는 로그인 상태(키 유무)를 다시 읽는지 본다 */
function renderKeys(handlers: Record<string, Handler>) {
  return renderRoutes(
    [
      {
        element: <RequireSignIn />,
        children: [{ path: "/keys", element: <KeysPage /> }],
      },
    ],
    "/keys",
    {
      "GET /api/me": () => json(SIGNED_IN_ME),
      "GET /api/notices/unread-count": () => json({ count: 0 }),
      ...handlers,
    },
  );
}

async function section(name: string) {
  return within(await screen.findByRole("region", { name }));
}

describe("KeysPage", () => {
  it("공급자마다 칸을 두고, 키가 없어도 발급 안내와 보내는 항목을 보인다", async () => {
    renderKeys({ "GET /api/llm-keys": () => json(NO_KEYS) });

    const openai = await section("OpenAI");
    expect(openai.getByText("저장한 키가 없습니다.")).toBeInTheDocument();
    expect(openai.getByRole("button", { name: "등록" })).toBeInTheDocument();
    expect(
      openai.queryByRole("button", { name: "다시 검증" }),
    ).not.toBeInTheDocument();
    expect(await section("Anthropic")).toBeDefined();

    const guide = await section("API 키 받는 법");
    expect(
      guide.getByText(
        /ChatGPT Plus · Claude Pro 같은 구독과 API 키는 다릅니다/,
      ),
    ).toBeInTheDocument();
    expect(
      guide.getByRole("link", { name: "OpenAI Platform" }),
    ).toHaveAttribute("href", "https://platform.openai.com/api-keys");
    expect(guide.getByRole("link", { name: "Claude Console" })).toHaveAttribute(
      "href",
      "https://platform.claude.com/settings/keys",
    );
    expect(
      guide.getByText(/콘솔에 결제 수단을 등록해야 키로 요청할 수 있습니다/),
    ).toBeInTheDocument();

    const transfer = await section("LLM 공급자에게 보내는 것");
    expect(
      transfer.getByText(
        "구독 주제, 지역 선호, 자유 텍스트 관심사, 원하는 분량",
      ),
    ).toBeInTheDocument();
    expect(transfer.getByText("후보 기사의 정보")).toBeInTheDocument();
    expect(
      transfer.getByText(
        "그 기간에 구독한 스토리라인의 변화 · 보도량 · 관심도 통계",
      ),
    ).toBeInTheDocument();
    expect(transfer.getByText("직전 리포트의 요약")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "저장하는 것과 보내는 것 모두 보기" }),
    ).toHaveAttribute("href", "/privacy");
  });

  it("키를 등록하면 입력을 비우고 힌트와 상태만 보이며 로그인 상태를 다시 읽는다", async () => {
    let keys = NO_KEYS;
    const { server } = renderKeys({
      "GET /api/llm-keys": () => json(keys),
      "PUT /api/llm-keys/ANTHROPIC": () => {
        keys = withAnthropic(STORED);
        return json({ result: "REGISTERED", failure: null, key: STORED });
      },
    });
    const user = userEvent.setup();
    const anthropic = await section("Anthropic");
    const meBefore = server.requests.filter((r) => r.path === "/api/me").length;

    await user.type(
      anthropic.getByLabelText("Anthropic API 키"),
      "sk-ant-test-0123456789WXYZ",
    );
    await user.click(anthropic.getByRole("button", { name: "등록" }));

    expect(await anthropic.findByRole("status")).toHaveTextContent(
      "키를 확인하고 저장했습니다.",
    );
    expect(bodiesOf(server, "PUT", "/api/llm-keys/ANTHROPIC")).toEqual([
      { key: "sk-ant-test-0123456789WXYZ" },
    ]);
    expect(await anthropic.findByText("…WXYZ")).toBeInTheDocument();
    expect(anthropic.getByText("쓸 수 있습니다")).toBeInTheDocument();
    expect(anthropic.getByLabelText("새 Anthropic API 키")).toHaveValue("");
    expect(
      screen.queryByText(/sk-ant-test-0123456789WXYZ/),
    ).not.toBeInTheDocument();
    await waitFor(() =>
      expect(
        server.requests.filter((r) => r.path === "/api/me").length,
      ).toBeGreaterThan(meBefore),
    );
  });

  it.each([
    ["INVALID_KEY", /공급자가 이 키를 받아 주지 않았습니다/],
    ["PERMISSION_DENIED", /이 키에는 필요한 권한이 없습니다/],
    ["QUOTA_EXHAUSTED", /잔액이나 지출 한도가 소진됐습니다/],
    ["TRANSIENT", /공급자가 지금 답하지 못했습니다/],
    ["UNEXPECTED", /예상하지 못한 답을 받았습니다/],
  ] as const)(
    "등록이 %s 로 거절되면 그 까닭을 알리고 키를 저장한 것으로 보이지 않는다",
    async (failure, text) => {
      renderKeys({
        "GET /api/llm-keys": () => json(NO_KEYS),
        "PUT /api/llm-keys/OPENAI": () =>
          json({ result: "REJECTED", failure, key: null }),
      });
      const user = userEvent.setup();
      const openai = await section("OpenAI");

      await user.type(openai.getByLabelText("OpenAI API 키"), "sk-bad");
      await user.click(openai.getByRole("button", { name: "등록" }));

      expect(await openai.findByRole("alert")).toHaveTextContent(text);
      expect(openai.getByText("저장한 키가 없습니다.")).toBeInTheDocument();
      // 거절된 키는 고쳐 다시 낼 수 있게 입력에 남는다
      expect(openai.getByLabelText("OpenAI API 키")).toHaveValue("sk-bad");
    },
  );

  it("힌트가 없는 키는 저장됨으로 보이고, 무효인 키는 새 키를 등록하라고 알린다", async () => {
    renderKeys({
      "GET /api/llm-keys": () =>
        json(withAnthropic({ ...STORED, hint: "", status: "INVALID" })),
    });

    const anthropic = await section("Anthropic");
    expect(anthropic.getByText("저장됨")).toBeInTheDocument();
    expect(
      anthropic.getByText(
        "무효입니다 — 공급자 콘솔에서 키를 확인하고 새 키를 등록해 주세요",
      ),
    ).toBeInTheDocument();
    expect(
      anthropic.getByRole("button", { name: "새 키로 바꾸기" }),
    ).toBeInTheDocument();
  });

  it("다시 검증의 결과를 갈래마다 알리고 키를 다시 읽는다", async () => {
    let keys = withAnthropic(STORED);
    let answer: components["schemas"]["KeyCheckResponse"] = {
      result: "VALID",
      failure: null,
      key: STORED,
    };
    const { server } = renderKeys({
      "GET /api/llm-keys": () => json(keys),
      "POST /api/llm-keys/ANTHROPIC/checks": () => json(answer),
    });
    const user = userEvent.setup();
    const anthropic = await section("Anthropic");
    const listed = () =>
      server.requests.filter(
        (r) => r.method === "GET" && r.path === "/api/llm-keys",
      ).length;

    const before = listed();
    await user.click(anthropic.getByRole("button", { name: "다시 검증" }));
    expect(await anthropic.findByRole("status")).toHaveTextContent(
      "키를 쓸 수 있습니다.",
    );
    await waitFor(() => expect(listed()).toBeGreaterThan(before));

    const invalid: LlmKey = { ...STORED, status: "INVALID" };
    keys = withAnthropic(invalid);
    answer = { result: "INVALID", failure: "INVALID_KEY", key: invalid };
    await user.click(anthropic.getByRole("button", { name: "다시 검증" }));
    expect(await anthropic.findByRole("alert")).toHaveTextContent(
      /키가 무효가 됐습니다\. 공급자가 이 키를 받아 주지 않았습니다/,
    );
    expect(await anthropic.findByText(/^무효입니다/)).toBeInTheDocument();

    answer = { result: "UNDETERMINED", failure: "TRANSIENT", key: invalid };
    await user.click(anthropic.getByRole("button", { name: "다시 검증" }));
    expect(await anthropic.findByRole("alert")).toHaveTextContent(
      /키를 확인하지 못했습니다\. 공급자가 지금 답하지 못했습니다/,
    );

    answer = { result: "MISSING", failure: null, key: null };
    await user.click(anthropic.getByRole("button", { name: "다시 검증" }));
    expect(await anthropic.findByRole("alert")).toHaveTextContent(
      "저장한 키가 없습니다.",
    );
  });

  it("삭제는 확인을 받은 뒤 지우고 키와 로그인 상태를 다시 읽는다", async () => {
    let keys = withAnthropic(STORED);
    const { server } = renderKeys({
      "GET /api/llm-keys": () => json(keys),
      "DELETE /api/llm-keys/ANTHROPIC": () => {
        keys = NO_KEYS;
        return noContent();
      },
    });
    const user = userEvent.setup();
    const anthropic = await section("Anthropic");
    const deleted = () =>
      server.requests.filter((r) => r.method === "DELETE").length;

    await user.click(anthropic.getByRole("button", { name: "삭제" }));
    const dialog = within(await screen.findByRole("alertdialog"));
    expect(dialog.getByText("Anthropic 키를 지울까요?")).toBeInTheDocument();
    await user.click(dialog.getByRole("button", { name: "취소" }));
    expect(deleted()).toBe(0);

    await user.click(anthropic.getByRole("button", { name: "삭제" }));
    await user.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", {
        name: "삭제",
      }),
    );

    expect(await anthropic.findByRole("status")).toHaveTextContent(
      "키를 지웠습니다.",
    );
    expect(deleted()).toBe(1);
    expect(
      await anthropic.findByText("저장한 키가 없습니다."),
    ).toBeInTheDocument();
  });

  it("서버가 오류로 답하면 상태 코드와 함께 알린다", async () => {
    renderKeys({
      "GET /api/llm-keys": () => json(NO_KEYS),
      "PUT /api/llm-keys/OPENAI": () =>
        problem(400, "요청 본문의 형식이 틀렸다"),
    });
    const user = userEvent.setup();
    const openai = await section("OpenAI");

    await user.type(openai.getByLabelText("OpenAI API 키"), "sk-x");
    await user.click(openai.getByRole("button", { name: "등록" }));

    expect(await openai.findByRole("alert")).toHaveTextContent(
      "요청이 실패했습니다 (HTTP 400). 다시 시도해 주세요.",
    );
  });

  it("키를 읽지 못하면 알리고 다시 시도하게 한다", async () => {
    let fail = true;
    renderKeys({
      "GET /api/llm-keys": () =>
        fail ? problem(500, "서버 오류") : json(NO_KEYS),
    });
    const user = userEvent.setup();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "LLM 키를 읽지 못했습니다.",
    );
    fail = false;
    await user.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(await section("OpenAI")).toBeDefined();
  });
});
