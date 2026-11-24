// 태그 체계 화면 시험 — 버전 목록 · 원문 보기, 원문 확인 · 활성화와 그 결과, 확인 뒤 끼어든 활성화 알림. 가짜 서버의 응답은 운영자 API 명세(api/openapi.json)의 모양이다.
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { Schemas } from "@/features/admin/adminClient";
import { renderAdminRoutes, TAG_SETS } from "@/features/admin/reviewTestKit";
import { TagSetsPage } from "@/features/admin/TagSetsPage";
import { textOffset } from "@/features/admin/textPosition";
import { formatDateTime } from "@/shared/lib/dateTime";
import { type Handler, json, problem } from "@/test/fakeServer";

const ACTIVE_TEXT =
  'questions:\n  topic: "주제"\nregions:\n  - key: other-foreign\n';
const OLD_TEXT = 'questions:\n  topic: "옛 주제"\n';
const NEW_TEXT = 'questions:\n  topic: "새 주제"\n';

const CHANGES: Schemas["TagSetChanges"] = {
  added: [{ kind: "TOPIC", key: "science.space" }],
  removed: [{ kind: "FORMAT", key: "column" }],
  revised: [{ kind: "REGION", key: "other-foreign" }],
  lineage: [
    {
      kind: "REGION",
      change: "SPLIT",
      from: ["other-foreign"],
      to: ["other-foreign", "middle-east"],
    },
  ],
  questionsRevised: true,
  importanceRevised: false,
  thresholds: [
    {
      current: { kind: "TOPIC", tagThreshold: 0.5, storeThreshold: 0.1 },
      proposed: { kind: "TOPIC", tagThreshold: 0.4, storeThreshold: 0.1 },
    },
  ],
};

const READY: Schemas["TagSetReviewResponse"] = {
  result: "READY",
  changes: CHANGES,
  recentArticles: 1234,
  problems: [],
};

const ACTIVATED: Schemas["TagSetActivationResponse"] = {
  result: "ACTIVATED",
  id: 9,
  number: 3,
  changes: CHANGES,
  requeued: 1200,
  problems: [],
};

function renderPage(handlers: Record<string, Handler> = {}) {
  return renderAdminRoutes([{ path: "/", element: <TagSetsPage /> }], "/", {
    "GET /api/admin/tag-sets": () => json(TAG_SETS),
    "GET /api/admin/tag-sets/7/text": () => json({ text: ACTIVE_TEXT }),
    "GET /api/admin/tag-sets/3/text": () => json({ text: OLD_TEXT }),
    "GET /api/admin/tag-sets/9/text": () => json({ text: NEW_TEXT }),
    "POST /api/admin/tag-set-reviews": () => json(READY),
    "POST /api/admin/tag-sets": () => json(ACTIVATED),
    ...handlers,
  }).server;
}

async function editor(): Promise<HTMLTextAreaElement> {
  return (await screen.findByRole("textbox", {
    name: "새 원문(YAML)",
  })) as HTMLTextAreaElement;
}

async function setDays(value: string) {
  const days = screen.getByLabelText("재평가 기간(일)");
  await userEvent.clear(days);
  if (value !== "") {
    await userEvent.type(days, value);
  }
}

async function review() {
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  return screen.findByRole("region", { name: "확인 결과" });
}

/** 확인 대화상자를 거쳐 활성화한다 */
async function activate() {
  await userEvent.click(screen.getByRole("button", { name: "활성화" }));
  const dialog = await screen.findByRole("alertdialog");
  await userEvent.click(within(dialog).getByRole("button", { name: "활성화" }));
  return screen.findByRole("region", { name: "활성화 결과" });
}

function bodies(server: ReturnType<typeof renderPage>, path: string) {
  return server.requests
    .filter((request) => request.method === "POST" && request.path === path)
    .map((request) => JSON.parse(request.body) as unknown);
}

describe("TagSetsPage", () => {
  it("버전 목록은 번호 · 활성 · 만든 이 · 시각을 보이고, 편집기는 활성 버전의 원문에서 시작한다", async () => {
    renderPage();

    expect(await editor()).toHaveValue(ACTIVE_TEXT);
    const rows = within(
      screen.getByRole("table", { name: "버전 목록" }),
    ).getAllByRole("row");
    expect(rows).toHaveLength(3);
    expect(rows[1]).toHaveTextContent(
      `2활성운영자${formatDateTime("2026-11-02T00:00:00Z")}원문 보기`,
    );
    expect(rows[2]).toHaveTextContent(
      `1기본 체계 파일${formatDateTime("2026-10-30T00:00:00Z")}원문 보기`,
    );
    expect(
      screen.getByRole("region", { name: "새 버전 만들기" }),
    ).toHaveTextContent("버전 2 원문에서 시작했다");
  });

  it("원문 보기는 그 버전의 원문을 읽기 전용으로 보이고, 다시 누르면 닫는다", async () => {
    renderPage();
    const show = await screen.findByRole("button", {
      name: "버전 1 원문 보기",
    });

    await userEvent.click(show);

    const view = await screen.findByRole("textbox", { name: "버전 1 원문" });
    await waitFor(() => expect(view).toHaveValue(OLD_TEXT));
    expect(view).toHaveAttribute("readonly");
    expect(show).toHaveAttribute("aria-pressed", "true");

    await userEvent.click(show);

    expect(
      screen.queryByRole("region", { name: "버전 1 원문" }),
    ).not.toBeInTheDocument();
  });

  it("확인은 원문 · 재평가 기간을 보내고 변경 목록과 재평가 대상 기사 수를 보인다", async () => {
    const server = renderPage();
    await userEvent.type(await editor(), "# 고침");
    await setDays("7");

    const result = await review();

    expect(bodies(server, "/api/admin/tag-set-reviews")).toEqual([
      { text: `${ACTIVE_TEXT}# 고침`, recentDays: 7 },
    ]);
    const list = within(result).getByLabelText("변경 목록");
    expect(list).toHaveTextContent("더한 키주제 science.space");
    expect(list).toHaveTextContent("계보 없이 지운 키형식 column");
    expect(list).toHaveTextContent(
      "이 키를 선호하던 사용자는 선호가 해제되고 알림을 받는다",
    );
    expect(list).toHaveTextContent(
      "고친 키(이름 · 설명 · 기본 제외)지역 other-foreign",
    );
    expect(list).toHaveTextContent(
      "계보지역 분할: other-foreign → other-foreign, middle-east",
    );
    expect(list).toHaveTextContent("문구질문 바뀜 · 중요도 기준 그대로");
    expect(list).toHaveTextContent(
      "주제 — 태그 기준 0.5 → 0.4 · 저장 기준 0.1 → 0.1",
    );
    expect(result).toHaveTextContent(
      "재평가 기간 7일에 처음 본 기사 1,234건 — 활성화하면 새 버전으로 다시 평가한다",
    );
  });

  it("재평가 기간 0일의 확인은 재평가하지 않는다고 알린다", async () => {
    renderPage({
      "POST /api/admin/tag-set-reviews": () =>
        json({ ...READY, recentArticles: 0 }),
    });
    await editor();

    expect(await review()).toHaveTextContent(
      "재평가 기간 0일 — 재평가하지 않는다",
    );
  });

  it("원문 문제는 줄:열 · 경로 · 문구로 보이고, 줄:열을 누르면 편집기의 그 자리로 간다", async () => {
    renderPage({
      "POST /api/admin/tag-set-reviews": () =>
        json({
          result: "INVALID_TAG_SET",
          changes: null,
          recentArticles: null,
          problems: [
            {
              path: "questions.topic",
              code: "TEMPLATE_PLACEHOLDER",
              message: "질문 틀에 {description} 이 하나 있어야 한다",
              line: 2,
              column: 3,
            },
          ],
        } satisfies Schemas["TagSetReviewResponse"]),
    });
    const text = await editor();

    const result = await review();
    expect(within(result).getByRole("alert")).toHaveTextContent(
      "원문에 문제가 1개 있다",
    );
    expect(within(result).getByRole("listitem")).toHaveTextContent(
      "2:3 questions.topic 질문 틀에 {description} 이 하나 있어야 한다",
    );

    await userEvent.click(within(result).getByRole("button", { name: "2:3" }));

    expect(text).toHaveFocus();
    expect(text.selectionStart).toBe(textOffset(ACTIVE_TEXT, 2, 3));
    expect(screen.getByRole("button", { name: "활성화" })).toBeDisabled();
  });

  it("활성 버전과 내용이 같으면 활성화할 것이 없다고 알리고 태그 기준 화면으로 잇는다", async () => {
    renderPage({
      "POST /api/admin/tag-set-reviews": () =>
        json({
          result: "UNCHANGED",
          changes: null,
          recentArticles: null,
          problems: [],
        } satisfies Schemas["TagSetReviewResponse"]),
    });
    await editor();

    const result = await review();

    expect(result).toHaveTextContent(
      "지금 활성 버전과 내용이 같다 — 활성화할 것이 없다",
    );
    expect(
      within(result).getByRole("link", { name: "태그 기준" }),
    ).toHaveAttribute("href", "/admin/thresholds");
    expect(screen.getByRole("button", { name: "활성화" })).toBeDisabled();
  });

  it("활성화는 활성화할 수 있다고 확인한 원문 · 재평가 기간 그대로일 때만 할 수 있다", async () => {
    renderPage();
    const text = await editor();
    const button = screen.getByRole("button", { name: "활성화" });
    expect(button).toBeDisabled();

    await review();
    expect(button).toBeEnabled();

    await userEvent.type(text, "x");
    expect(button).toBeDisabled();
    expect(screen.getByRole("region", { name: "확인 결과" })).toHaveTextContent(
      "확인한 뒤 원문 · 재평가 기간이 바뀌었다",
    );

    await userEvent.type(text, "{Backspace}");
    expect(button).toBeEnabled();
    await setDays("1");
    expect(button).toBeDisabled();
  });

  it("재평가 기간이 0 ~ 상한 밖이거나 정수가 아니면 확인하지 않는다", async () => {
    const server = renderPage();
    await editor();

    await setDays("31");

    expect(
      screen.getByText("재평가 기간은 0일에서 30일까지의 정수다"),
    ).toBeVisible();
    expect(screen.getByRole("button", { name: "확인" })).toBeDisabled();
    await setDays("");
    expect(screen.getByRole("button", { name: "확인" })).toBeDisabled();
    await setDays("30");
    expect(screen.getByRole("button", { name: "확인" })).toBeEnabled();
    expect(bodies(server, "/api/admin/tag-set-reviews")).toEqual([]);
  });

  it("확인이 거절되면 서버의 설명을 보인다", async () => {
    renderPage({
      "POST /api/admin/tag-set-reviews": () =>
        problem(400, "재평가 기간은 0일에서 30일까지다"),
    });
    await editor();

    await userEvent.click(screen.getByRole("button", { name: "확인" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "재평가 기간은 0일에서 30일까지다",
    );
  });

  it("활성화는 확인 대화상자를 거친다 — 취소하면 보내지 않는다", async () => {
    const server = renderPage();
    await editor();
    await setDays("7");
    await review();

    await userEvent.click(screen.getByRole("button", { name: "활성화" }));
    const dialog = await screen.findByRole("alertdialog");
    expect(dialog).toHaveTextContent("새 버전으로 활성화할까요?");
    expect(dialog).toHaveTextContent(
      "계보 없이 지운 키 1개 — 그 키를 선호하던 사용자의 선호가 해제된다",
    );
    expect(dialog).toHaveTextContent(
      "재평가 기간 7일 — 기사 1,234건을 다시 평가한다",
    );
    await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));

    await waitFor(() =>
      expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument(),
    );
    expect(bodies(server, "/api/admin/tag-sets")).toEqual([]);
  });

  it("활성화는 확인한 원문 · 기간을 보내고 새 버전 번호와 재평가 대기 건수를 보인다", async () => {
    const server = renderPage();
    await userEvent.type(await editor(), "# 고침");
    await setDays("7");
    await review();

    const result = await activate();

    expect(bodies(server, "/api/admin/tag-sets")).toEqual([
      { text: `${ACTIVE_TEXT}# 고침`, recentDays: 7 },
    ]);
    expect(within(result).getByRole("status")).toHaveTextContent(
      "활성화했다 — 새 버전 3(ID 9). 재평가 대기에 넣거나 실패에서 되돌린 기사 1,200건",
    );
    expect(result).toHaveTextContent("변경 목록은 확인 결과와 같다.");
    // 대조: 확인과 같은 결과면 끼어든 활성화를 알리지 않는다
    expect(within(result).queryByRole("alert")).toBeNull();
    // 활성화한 원문은 다시 확인할 때까지 거듭 활성화하지 않는다
    expect(screen.getByRole("button", { name: "활성화" })).toBeDisabled();
    // 버전 목록을 다시 읽는다
    await waitFor(() =>
      expect(
        server.requests.filter(
          (request) =>
            request.method === "GET" && request.path === "/api/admin/tag-sets",
        ),
      ).toHaveLength(2),
    );
  });

  it("확인 뒤 다른 활성화가 끼어 변경 목록이 달라지면 활성화 결과를 정본으로 알린다", async () => {
    renderPage({
      "POST /api/admin/tag-sets": () =>
        json({
          ...ACTIVATED,
          changes: { ...CHANGES, removed: [] },
        } satisfies Schemas["TagSetActivationResponse"]),
    });
    await editor();
    await review();

    const result = await activate();

    expect(within(result).getByRole("alert")).toHaveTextContent(
      "확인 뒤 다른 활성화가 끼어 결과가 확인과 다르다 — 아래 활성화 결과가 정본이다",
    );
    expect(within(result).getByLabelText("변경 목록")).toHaveTextContent(
      "계보 없이 지운 키없음",
    );
  });

  it("확인 뒤 끼어든 활성화로 활성 버전과 같은 내용이 되면 아무것도 하지 않았다고 알린다", async () => {
    renderPage({
      "POST /api/admin/tag-sets": () =>
        json({
          result: "UNCHANGED",
          id: null,
          number: null,
          changes: null,
          requeued: null,
          problems: [],
        } satisfies Schemas["TagSetActivationResponse"]),
    });
    await editor();
    await review();

    const result = await activate();

    expect(within(result).getByRole("alert")).toHaveTextContent(
      "확인 뒤 다른 활성화가 끼어 결과가 확인과 다르다",
    );
    expect(result).toHaveTextContent(
      "활성 버전과 내용이 같아 아무것도 하지 않았다.",
    );
  });

  it("확인 뒤 끼어든 활성화로 원문이 대조에 걸리면 그 문제를 보인다", async () => {
    renderPage({
      "POST /api/admin/tag-sets": () =>
        json({
          result: "INVALID_TAG_SET",
          id: null,
          number: null,
          changes: null,
          requeued: null,
          problems: [
            {
              path: "lineage[0].from[0]",
              code: "LINEAGE_FROM_UNKNOWN",
              message: "기준 버전에 없는 키다: other-foreign",
              line: 4,
              column: 5,
            },
          ],
        } satisfies Schemas["TagSetActivationResponse"]),
    });
    await editor();
    await review();

    const result = await activate();

    expect(within(result).getByRole("alert")).toBeInTheDocument();
    expect(result).toHaveTextContent(
      "원문에 문제가 있어 아무것도 하지 않았다.",
    );
    expect(within(result).getByRole("listitem")).toHaveTextContent(
      "4:5 lineage[0].from[0] 기준 버전에 없는 키다: other-foreign",
    );
  });

  it("활성화한 뒤 새 버전의 원문으로 다시 시작한다", async () => {
    const server = renderPage();
    await userEvent.type(await editor(), "# 고침");
    await review();
    await activate();

    await userEvent.click(
      screen.getByRole("button", { name: "버전 3 원문으로 다시 시작" }),
    );

    // 활성화한 원문을 고치지 않았으니 묻지 않고 바꾼다
    await waitFor(async () => expect(await editor()).toHaveValue(NEW_TEXT));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("region", { name: "활성화 결과" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("region", { name: "새 버전 만들기" }),
    ).toHaveTextContent("버전 3 원문에서 시작했다");
    expect(
      server.requests.some(
        (request) => request.path === "/api/admin/tag-sets/9/text",
      ),
    ).toBe(true);
  });

  it("예전 버전의 원문을 편집기로 불러온다 — 고친 원문이 있으면 버릴지 묻는다", async () => {
    renderPage();
    const text = await editor();
    await userEvent.type(text, "x");
    await userEvent.click(
      screen.getByRole("button", { name: "버전 1 원문 보기" }),
    );
    const view = await screen.findByRole("region", { name: "버전 1 원문" });
    const load = within(view).getByRole("button", {
      name: "편집기로 불러오기",
    });
    await waitFor(() => expect(load).toBeEnabled());

    await userEvent.click(load);
    const dialog = await screen.findByRole("alertdialog");
    expect(dialog).toHaveTextContent("고친 원문을 버릴까요?");
    expect(dialog).toHaveTextContent("버전 1 원문으로 바꾸고");
    await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));
    await waitFor(() =>
      expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument(),
    );
    expect(await editor()).toHaveValue(`${ACTIVE_TEXT}x`);

    await userEvent.click(load);
    await userEvent.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", {
        name: "바꾸기",
      }),
    );

    await waitFor(async () => expect(await editor()).toHaveValue(OLD_TEXT));
    expect(
      screen.getByRole("region", { name: "새 버전 만들기" }),
    ).toHaveTextContent("버전 1 원문에서 시작했다");
  });

  it("고치지 않은 편집기에는 묻지 않고 불러온다", async () => {
    renderPage();
    await editor();
    await userEvent.click(
      screen.getByRole("button", { name: "버전 1 원문 보기" }),
    );
    const view = await screen.findByRole("region", { name: "버전 1 원문" });
    const load = within(view).getByRole("button", {
      name: "편집기로 불러오기",
    });
    await waitFor(() => expect(load).toBeEnabled());

    await userEvent.click(load);

    await waitFor(async () => expect(await editor()).toHaveValue(OLD_TEXT));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("원문을 불러온 횟수와 ID 가 같은 버전의 원문을 봐도 원문 · 편집기는 하나씩이다", async () => {
    // 버전 ID 는 1부터 오고, 편집기는 원문을 불러올 때마다 새로 그린다 — 둘이 같은 수가 되는 화면(ID = 번호: 버전 2 는 ID 2, 버전 1 은 ID 1)
    const versions: Schemas["TagSetListResponse"] = {
      ...TAG_SETS,
      versions: TAG_SETS.versions.map((version) => ({
        ...version,
        id: version.number,
      })),
    };
    renderPage({
      "GET /api/admin/tag-sets": () => json(versions),
      "GET /api/admin/tag-sets/2/text": () => json({ text: ACTIVE_TEXT }),
      "GET /api/admin/tag-sets/1/text": () => json({ text: OLD_TEXT }),
    });
    await editor();
    await userEvent.click(
      screen.getByRole("button", { name: "버전 1 원문 보기" }),
    );
    const view = await screen.findByRole("region", { name: "버전 1 원문" });
    const load = within(view).getByRole("button", {
      name: "편집기로 불러오기",
    });
    await waitFor(() => expect(load).toBeEnabled());

    await userEvent.click(load);
    await waitFor(async () => expect(await editor()).toHaveValue(OLD_TEXT));
    await userEvent.type(await editor(), "x");
    await userEvent.click(load);
    await userEvent.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", {
        name: "취소",
      }),
    );
    await waitFor(() =>
      expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument(),
    );

    expect(screen.getAllByRole("region", { name: "버전 1 원문" })).toHaveLength(
      1,
    );
    expect(
      screen.getAllByRole("region", { name: "새 버전 만들기" }),
    ).toHaveLength(1);
    expect(await editor()).toHaveValue(`${OLD_TEXT}x`);
  });

  it("버전 목록을 읽지 못하면 그 문구를 보인다", async () => {
    renderPage({
      "GET /api/admin/tag-sets": () => problem(500, "서버 오류"),
    });

    expect(await screen.findByRole("alert")).toHaveTextContent("서버 오류");
    expect(
      screen.queryByRole("textbox", { name: "새 원문(YAML)" }),
    ).not.toBeInTheDocument();
  });
});
