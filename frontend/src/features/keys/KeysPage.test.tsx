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
    { provider: "OPENROUTER", key: null },
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
      { provider: "OPENROUTER", key: null },
    ],
  };
}

/** 로그인 틀 안에 그린다 — 키가 바뀌면 틀이 읽는 로그인 상태(키 유무)를 다시 읽는지 본다 */
function renderKeys(handlers: Record<string, Handler>) {
  return renderRoutes(
    [
      {
        element: <RequireSignIn />,
        children: [
          { path: "/keys", element: <KeysPage /> },
          { path: "/interests", element: <p>관심 설정 화면</p> },
        ],
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
    const openRouter = await section("OpenRouter");
    expect(openRouter.getByText("저장한 키가 없습니다.")).toBeInTheDocument();
    expect(
      openRouter.getByRole("button", { name: "등록" }),
    ).toBeInTheDocument();

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
    expect(guide.getByRole("link", { name: "OpenRouter" })).toHaveAttribute(
      "href",
      "https://openrouter.ai/settings/keys",
    );
    expect(
      guide.getByText(/콘솔에 결제 수단을 등록해야 키로 요청할 수 있습니다/),
    ).toBeInTheDocument();

    const transfer = await section("LLM 공급자에게 보내는 것");
    // 키 화면도 같은 고지 — OpenRouter 의 중계 경로
    expect(
      transfer.getByText(/OpenRouter를 거쳐, 그 모델을 만든 회사나/),
    ).toBeInTheDocument();
    expect(transfer.getByText("구독 주제, 지역 선호")).toBeInTheDocument();
    expect(
      transfer.getByText("시간대 — 기사의 발행 시각을 그 시간대로 적습니다"),
    ).toBeInTheDocument();
    expect(transfer.getByText("요약 길이")).toBeInTheDocument();
    expect(
      transfer.getByText(
        "다이제스트 항목(주요 뉴스 · 헤드라인)마다 사건으로 묶인 표본 기사의 정보(제목 · 요약문 · 출처 · 발행 시각 · 주제 · 지역)",
      ),
    ).toBeInTheDocument();
    // 묶음 이름 · 대표 기사 제목은 보내지 않는다
    expect(transfer.queryByText(/큰 스토리라인 묶음/)).not.toBeInTheDocument();
    // 사용자 스토리라인의 정리를 만들 때 가는 것
    expect(
      transfer.getByRole("heading", { name: "관심이 쌓인 흐름을 정리할 때" }),
    ).toBeInTheDocument();
    expect(
      transfer.getByText("그 흐름의 사건마다 날짜와 대표 기사의 제목 · 요약문"),
    ).toBeInTheDocument();
    expect(
      transfer.getByText("그 흐름의 지난 정리(제목 · 흐름 요약 · 최근 전개)"),
    ).toBeInTheDocument();
    // 변화 리포트를 만들 때 가는 것
    expect(
      transfer.getByRole("heading", { name: "변화 리포트를 만들 때" }),
    ).toBeInTheDocument();
    expect(transfer.getByText("리포트 기간(날짜)")).toBeInTheDocument();
    expect(
      transfer.getByText(
        "추적한 흐름마다 그 흐름의 정리(제목 · 흐름 요약 · 최근 전개), 상태, 기간 안에 추적을 더했는지, 주간 리포트는 직전 주간과 견준 보도의 변화(늘었음 · 줄었음 같은 판정 — 기사 수는 보내지 않는다)",
      ),
    ).toBeInTheDocument();
    expect(
      transfer.getByText(
        "추적한 흐름의 그 기간 사건마다 대표 기사의 제목 · 날짜 · 기간 안에 시작했는지",
      ),
    ).toBeInTheDocument();
    expect(
      transfer.queryByText(
        "구독 주제의 이름과 직전 기간과 견준 보도의 변화(판정)",
      ),
    ).not.toBeInTheDocument();
    expect(
      transfer.getByText(
        "관심 변화 — 새로 생기거나 사라진 주제 · 흐름의 관심(관심 값은 보내지 않는다), 흐름의 정리, 기간 안에 바꾼 주제 구독(시각 · 구독 · 해제 · 주제 이름)",
      ),
    ).toBeInTheDocument();
    expect(
      transfer.getByText(
        "직전 리포트의 글(섹션 제목 · 본문) — 월간 리포트는 그 달 주간 리포트의 글도",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "저장하는 것과 보내는 것 모두 보기" }),
    ).toHaveAttribute("href", "/privacy");
  });

  it("키를 등록하면 입력을 비우고 힌트와 상태만 보이며 로그인 상태를 다시 읽는다", async () => {
    let keys = NO_KEYS;
    const { server, router } = renderKeys({
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
    // 등록 전 로그인 상태가 키를 요구하지 않았으면(첫 키가 아니면) 키 화면에 남는다
    expect(router.state.location.pathname).toBe("/keys");
  });

  it("처음 키를 등록하면 관심 설정 화면으로 간다", async () => {
    let keys = NO_KEYS;
    const { router } = renderKeys({
      "GET /api/me": () =>
        json({ ...SIGNED_IN_ME, llmKeyRequired: keys === NO_KEYS }),
      "GET /api/llm-keys": () => json(keys),
      "PUT /api/llm-keys/ANTHROPIC": () => {
        keys = withAnthropic(STORED);
        return json({ result: "REGISTERED", failure: null, key: STORED });
      },
    });
    const user = userEvent.setup();
    const anthropic = await section("Anthropic");

    await user.type(
      anthropic.getByLabelText("Anthropic API 키"),
      "sk-ant-test-0123456789WXYZ",
    );
    await user.click(anthropic.getByRole("button", { name: "등록" }));

    expect(await screen.findByText("관심 설정 화면")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/interests");
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

describe("KeysPage 요청 제한", () => {
  it("등록 · 다시 검증이 요청 제한에 걸리면 축을 말하지 않는 요청 제한 안내를 보인다", async () => {
    const limited = () =>
      new Response(
        JSON.stringify({ type: "urn:yozmnews:problem:rate-limited" }),
        {
          status: 429,
          headers: {
            "Content-Type": "application/problem+json",
            "Retry-After": "1800",
          },
        },
      );
    renderKeys({
      "GET /api/llm-keys": () =>
        json({
          providers: [
            { provider: "OPENAI", key: STORED },
            { provider: "ANTHROPIC", key: null },
          ],
        }),
      "PUT /api/llm-keys/OPENAI": limited,
      "POST /api/llm-keys/OPENAI/checks": limited,
    });
    const user = userEvent.setup();
    const openai = await section("OpenAI");

    await user.type(openai.getByLabelText("새 OpenAI API 키"), "sk-new");
    await user.click(openai.getByRole("button", { name: "새 키로 바꾸기" }));
    // 키 확인은 세션 사용자 · 클라이언트 주소 두 축으로 센다(account-R30) — 어느 쪽에 걸렸는지 모르므로 "이 네트워크" 라고 하지 않는다
    const registerAlert = await openai.findByRole("alert");
    expect(registerAlert.textContent).toBe(
      "요청이 너무 많아 잠시 막혔습니다. 30분 뒤에 다시 시도해 주세요.",
    );
    expect(registerAlert.textContent).not.toContain("이 네트워크");

    await user.click(openai.getByRole("button", { name: "다시 검증" }));
    await waitFor(() =>
      expect(
        openai.getAllByRole("alert").map((alert) => alert.textContent),
      ).toContain(
        "요청이 너무 많아 잠시 막혔습니다. 30분 뒤에 다시 시도해 주세요.",
      ),
    );
    expect(
      openai
        .getAllByRole("alert")
        .some((alert) => alert.textContent?.includes("이 네트워크")),
    ).toBe(false);
  });
});
