import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { Schemas } from "@/features/admin/adminClient";
import { json } from "@/test/fakeServer";
import { OVERVIEW, renderAdminRoutes } from "@/features/admin/reviewTestKit";
import { ThresholdsPage } from "@/features/admin/ThresholdsPage";

const topic = (tag: number, store: number) => ({
  kind: "TOPIC" as const,
  tagThreshold: tag,
  storeThreshold: store,
});

const STATUS: Schemas["ThresholdStatusResponse"] = {
  version: 7,
  versionNumber: 2,
  topic: {
    current: topic(0.5, 0.1),
    pastMaxStoreThreshold: 0.2,
    conditions: ["STORE_THRESHOLD_CHANGED"],
  },
  region: {
    current: { kind: "REGION", tagThreshold: 0.5, storeThreshold: 0.1 },
    pastMaxStoreThreshold: 0.1,
    conditions: [],
  },
  changes: [
    {
      kind: "TOPIC",
      previousTagThreshold: 0.5,
      previousStoreThreshold: 0.2,
      tagThreshold: 0.5,
      storeThreshold: 0.1,
      actor: "OPERATOR",
      changedAt: "2026-11-03T00:00:00Z",
    },
  ],
};

const PREVIEW: Schemas["ThresholdPreviewResponse"] = {
  result: "PREVIEWED",
  version: 7,
  current: topic(0.5, 0.1),
  proposed: topic(0.4, 0.1),
  warnings: ["TAG_BELOW_PAST_STORE"],
  evaluations: 120,
  changed: 7,
  before: OVERVIEW,
  after: {
    ...OVERVIEW,
    topicStatus: {
      ...OVERVIEW.topicStatus,
      tagged: { count: 3, ratio: 0.75 },
      lowConfidence: { count: 0, ratio: 0 },
    },
  },
};

function renderPage(change: Schemas["ThresholdChangeResponse"]) {
  return renderAdminRoutes([{ path: "/", element: <ThresholdsPage /> }], "/", {
    "GET /api/admin/thresholds": () => json(STATUS),
    "POST /api/admin/threshold-previews": () => json(PREVIEW),
    "PUT /api/admin/thresholds/TOPIC": () => json(change),
  }).server;
}

const CHANGED: Schemas["ThresholdChangeResponse"] = {
  result: "CHANGED",
  version: 7,
  previous: topic(0.5, 0.1),
  current: topic(0.4, 0.1),
  warnings: ["TAG_BELOW_PAST_STORE"],
};

async function previewTopicTag(value: string) {
  const tag = await screen.findByLabelText("태그 기준");
  await userEvent.clear(tag);
  await userEvent.type(tag, value);
  await userEvent.click(screen.getByRole("button", { name: "미리보기" }));
  await screen.findByRole("region", { name: "미리보기" });
}

describe("ThresholdsPage", () => {
  it("지금 값 · 어긋난 상태 · 이력을 보인다", async () => {
    renderPage(CHANGED);

    const topicStatus = await screen.findByLabelText("주제");
    expect(topicStatus).toHaveTextContent(
      "주제 — 태그 기준 0.5 · 저장 기준 0.1 (지난 저장 기준 최댓값 0.2)",
    );
    expect(topicStatus).toHaveTextContent(
      "이 버전의 평가마다 저장된 확률 범위가 다르다 — 저장 기준이 바뀌었고 빠진 태그 채움이 그 차이를 다 메우지 않았다",
    );
    expect(screen.getByLabelText("지역")).toHaveTextContent("어긋난 상태 없음");
    const history = screen.getByRole("table", { name: "이력" });
    expect(within(history).getAllByRole("row")[1]).toHaveTextContent(
      "주제0.5 → 0.50.2 → 0.1운영자",
    );
  });

  it("저장 기준을 낮춘 미리보기의 경고는 빠진 태그 채움을 안내한다", async () => {
    renderAdminRoutes([{ path: "/", element: <ThresholdsPage /> }], "/", {
      "GET /api/admin/thresholds": () => json(STATUS),
      "POST /api/admin/threshold-previews": () =>
        json({
          ...PREVIEW,
          proposed: topic(0.5, 0.05),
          warnings: ["STORE_THRESHOLD_LOWERED"],
        }),
    });

    await previewTopicTag("0.4");

    expect(screen.getByRole("list", { name: "경고" })).toHaveTextContent(
      "저장 기준을 낮췄다 — 지난 평가에는 낮춘 구간의 확률이 없다. 빠진 태그 채움으로 채울 수 있다",
    );
  });

  it("미리보기는 입력 · 기간을 보내고 바뀐 평가 수 · 경고 · 바뀐 줄만 보인다", async () => {
    const server = renderPage(CHANGED);
    expect(await screen.findByRole("button", { name: "저장" })).toBeDisabled();

    await previewTopicTag("0.4");

    const body = JSON.parse(
      server.requests.find(
        (request) => request.path === "/api/admin/threshold-previews",
      )?.body ?? "{}",
    );
    expect(body).toMatchObject({
      kind: "TOPIC",
      tagThreshold: 0.4,
      storeThreshold: 0.1,
    });
    expect(typeof body.from).toBe("string");
    const preview = screen.getByRole("region", { name: "미리보기" });
    expect(preview).toHaveTextContent(
      "기간 안 평가 120건 가운데 7건의 주제 판정이 달라진다",
    );
    expect(preview).toHaveTextContent(
      "태그 기준을 지난 저장 기준 최댓값보다 낮췄다 — 그보다 높은 저장 기준으로 저장된 평가에는 그 구간 태그가 붙지 않는다",
    );
    const rows = within(
      screen.getByRole("table", { name: "전후" }),
    ).getAllByRole("row");
    // 머리줄 + 바뀐 줄 둘(태그 기준을 넘은 주제 있음 2 → 3, low_confidence 1 → 0)
    expect(rows).toHaveLength(3);
    expect(screen.getByRole("button", { name: "저장" })).toBeEnabled();
  });

  it("비었거나 수가 아닌 기준은 미리 보지 않는다", async () => {
    const server = renderPage(CHANGED);
    const tag = await screen.findByLabelText("태그 기준");
    const button = screen.getByRole("button", { name: "미리보기" });
    // 대조: 지금 값이 채워진 처음에는 미리 볼 수 있다
    expect(button).toBeEnabled();

    await userEvent.clear(tag);
    expect(button).toBeDisabled();
    expect(
      screen.getByText("태그 기준 · 저장 기준에 수를 넣는다"),
    ).toBeVisible();
    await userEvent.type(tag, "abc");
    expect(button).toBeDisabled();
    expect(
      server.requests.some(
        (request) => request.path === "/api/admin/threshold-previews",
      ),
    ).toBe(false);
  });

  it("저장한 뒤에는 다시 미리 볼 때까지 저장을 거듭하지 않는다", async () => {
    const server = renderPage(CHANGED);
    await previewTopicTag("0.4");

    await userEvent.click(screen.getByRole("button", { name: "저장" }));
    await screen.findByRole("region", { name: "저장 결과" });

    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
    expect(
      server.requests.filter((request) => request.method === "PUT"),
    ).toHaveLength(1);
  });

  it("미리보기 뒤 입력을 바꾸면 다시 미리 볼 때까지 저장할 수 없다", async () => {
    renderPage(CHANGED);
    await previewTopicTag("0.4");

    await userEvent.type(screen.getByLabelText("태그 기준"), "5");

    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  });

  it("저장은 미리 본 값을 보내고, 끼어든 변경이 없으면 알리지 않는다", async () => {
    const server = renderPage(CHANGED);
    await previewTopicTag("0.4");

    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    const result = await screen.findByRole("region", { name: "저장 결과" });
    expect(result).toHaveTextContent(
      "바꿨다 — 버전 ID 7, 주제 태그 기준 0.5 → 0.4 · 저장 기준 0.1 → 0.1",
    );
    const put = server.requests.find((request) => request.method === "PUT");
    expect(put?.path).toBe("/api/admin/thresholds/TOPIC");
    expect(JSON.parse(put?.body ?? "{}")).toEqual({
      tagThreshold: 0.4,
      storeThreshold: 0.1,
    });
    // 대조: 버전 · 이전 값이 미리보기와 같으면 알림이 없다
    expect(within(result).queryByRole("alert")).toBeNull();
  });

  it("저장 결과의 버전이 미리보기와 다르면 끼어든 변경을 알린다", async () => {
    renderPage({ ...CHANGED, version: 8 });
    await previewTopicTag("0.4");

    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "미리보기 뒤 다른 변경이 있었다 — 아래 저장 결과가 정본이다",
    );
  });

  it("저장 결과의 이전 값이 미리보기의 지금 값과 다르면 끼어든 변경을 알린다", async () => {
    renderPage({ ...CHANGED, previous: topic(0.45, 0.1) });
    await previewTopicTag("0.4");

    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "미리보기 뒤 다른 변경이 있었다",
    );
  });
});
