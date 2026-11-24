import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  type InterestModel,
  type InterestSettings,
  type InterestSources,
  type InterestTags,
  type InterestVolume,
} from "@/features/interests/interestQueries";
import { InterestsPage } from "@/features/interests/InterestsPage";
import { json, type Handler } from "@/test/fakeServer";
import { bodiesOf, renderRoutes } from "@/test/renderRoutes";

/** 체계 버전 1 — 분야 둘, 지역 하나, 형식 둘(하나는 기본 제외) */
const TAG_SET: InterestTags["tagSet"] = {
  id: 1,
  number: 1,
  topics: [
    {
      key: "politics",
      name: "정치",
      subtopics: [{ key: "politics.executive", name: "행정" }],
    },
    {
      key: "economy",
      name: "경제",
      subtopics: [
        { key: "economy.markets", name: "시장" },
        { key: "economy.industry", name: "산업" },
      ],
    },
  ],
  regions: [{ key: "china", name: "중국" }],
  formats: [
    { key: "brief", name: "단신·공지", excludedByDefault: true },
    { key: "analysis", name: "분석", excludedByDefault: false },
  ],
};

/** 새 계정 — 구독 없음, 기본 제외 형식만 */
const NEW_ACCOUNT: InterestTags = {
  tagSet: TAG_SET,
  preferences: { topics: [], excludedRegions: [], excludedFormats: ["brief"] },
};

const SOURCES: InterestSources = {
  sources: [
    { id: 3, name: null },
    { id: 5, name: "알림일보" },
  ],
  excluded: [],
};

const SETTINGS: InterestSettings = {
  timezone: "Asia/Seoul",
  digestTime: "07:00",
  weeklyReport: true,
  weeklyReportDay: "MONDAY",
  monthlyReport: true,
};

/** 새 계정의 분량 — 보통 · 큰 화제 3 · 요약 길이 보통 */
const VOLUME: InterestVolume = {
  volume: "NORMAL",
  customCount: null,
  bigSlots: 3,
  summaryLength: "NORMAL",
  presets: [
    { volume: "FEW", count: 20 },
    { volume: "NORMAL", count: 40 },
    { volume: "MANY", count: 60 },
  ],
  countRange: { min: 10, max: 100 },
  bigSlotRange: { min: 0, max: 10 },
  summaryLengths: ["SHORT", "NORMAL", "DETAILED"],
};

const NO_KEY_MODEL: InterestModel = {
  choosable: [],
  chosen: null,
  effective: null,
  reason: "NOT_CHOSEN",
  effort: "MEDIUM",
  efforts: ["LOW", "MEDIUM", "HIGH"],
};

function renderInterests(
  handlers: Record<string, Handler>,
  entry = "/interests",
) {
  return renderRoutes(
    [{ path: "/interests", element: <InterestsPage /> }],
    entry,
    {
      "GET /api/interests/tags": () => json(NEW_ACCOUNT),
      "GET /api/interests/sources": () => json(SOURCES),
      "GET /api/interests/settings": () => json(SETTINGS),
      "GET /api/interests/model": () => json(NO_KEY_MODEL),
      "GET /api/interests/volume": () => json(VOLUME),
      ...handlers,
    },
  );
}

async function section(name: string) {
  return within(await screen.findByRole("region", { name }));
}

afterEach(() => {
  delete (Element.prototype as { scrollIntoView?: unknown }).scrollIntoView;
});

describe("InterestsPage", () => {
  it("주소에 절의 id 가 있으면 조회가 모두 끝난 뒤 그 절로 한 번 내려간다", async () => {
    // jsdom 에는 scrollIntoView 가 없다
    const scroll = vi.fn();
    Element.prototype.scrollIntoView = scroll;
    renderInterests({}, "/interests#interest-model");

    await section("모델");
    await waitFor(() => expect(scroll).toHaveBeenCalledTimes(1));
    expect((scroll.mock.contexts[0] as Element).id).toBe("interest-model");
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(scroll).toHaveBeenCalledTimes(1);
  });

  it("다른 절의 조회가 끝나기 전에는 내려가지 않는다", async () => {
    const scroll = vi.fn();
    Element.prototype.scrollIntoView = scroll;
    let release: () => void = () => {};
    const modelHeld = new Promise<void>((resolve) => {
      release = resolve;
    });
    renderInterests(
      {
        "GET /api/interests/model": async () => {
          await modelHeld;
          return json(NO_KEY_MODEL);
        },
      },
      "/interests#interest-model",
    );

    await section("구독할 주제");
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(scroll).not.toHaveBeenCalled();
    release();
    await waitFor(() => expect(scroll).toHaveBeenCalledTimes(1));
    expect((scroll.mock.contexts[0] as Element).id).toBe("interest-model");
  });

  it("주소에 절의 id 가 없으면 내려가지 않는다", async () => {
    const scroll = vi.fn();
    Element.prototype.scrollIntoView = scroll;
    renderInterests({});

    await section("모델");
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(scroll).not.toHaveBeenCalled();
  });

  it("새 계정은 구독이 없다고 안내하고 기본 제외 형식만 켜져 있다", async () => {
    renderInterests({});

    const topics = await section("구독할 주제");
    expect(topics.getByRole("note")).toHaveTextContent(
      "구독한 주제가 없어 다이제스트에 구독 기사가 들어가지 않습니다. 관심 있는 주제를 고르세요.",
    );
    expect(topics.getByRole("checkbox", { name: "정치" })).not.toBeChecked();
    const formats = await section("뺄 형식");
    expect(formats.getByRole("checkbox", { name: "단신·공지" })).toBeChecked();
    expect(formats.getByRole("checkbox", { name: "분석" })).not.toBeChecked();
  });

  it("분야를 켜면 그 아래 주제가 켜진 채 잠기고, 저장하면 고른 키를 보내고 알린다", async () => {
    let saved = NEW_ACCOUNT;
    const { server } = renderInterests({
      "GET /api/interests/tags": () => json(saved),
      "PUT /api/interests/tags": () => {
        saved = {
          tagSet: TAG_SET,
          preferences: {
            topics: ["economy", "politics.executive"],
            excludedRegions: ["china"],
            excludedFormats: ["brief"],
          },
        };
        return json({
          result: "SAVED",
          preferences: saved.preferences,
          activeTagSetId: null,
        });
      },
    });
    const user = userEvent.setup();
    const topics = await section("구독할 주제");

    await user.click(topics.getByRole("checkbox", { name: "경제" }));
    expect(topics.getByRole("checkbox", { name: "시장" })).toBeChecked();
    expect(topics.getByRole("checkbox", { name: "산업" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    await user.click(topics.getByRole("checkbox", { name: "행정" }));
    await user.click(
      (await section("뺄 지역")).getByRole("checkbox", { name: "중국" }),
    );
    await user.click(
      screen.getByRole("button", { name: "주제 · 지역 · 형식 저장" }),
    );

    expect(await screen.findByRole("status")).toHaveTextContent(
      "주제 · 지역 · 형식을 저장했습니다.",
    );
    expect(bodiesOf(server, "PUT", "/api/interests/tags")).toEqual([
      {
        tagSetId: 1,
        topics: ["economy", "politics.executive"],
        excludedRegions: ["china"],
        excludedFormats: ["brief"],
      },
    ]);
    // 다시 읽은 서버 값으로 채워지고, 구독이 생겨 안내가 사라진다
    expect(
      (await section("구독할 주제")).queryByRole("note"),
    ).not.toBeInTheDocument();
    expect(
      (await section("구독할 주제")).getByRole("checkbox", { name: "경제" }),
    ).toBeChecked();
  });

  it("그사이 태그 체계가 바뀌었으면 다시 읽고 다시 저장하라고 알린다", async () => {
    let reads = 0;
    renderInterests({
      "GET /api/interests/tags": () => {
        reads += 1;
        return json(
          reads === 1
            ? NEW_ACCOUNT
            : { ...NEW_ACCOUNT, tagSet: { ...TAG_SET, id: 2, number: 2 } },
        );
      },
      "PUT /api/interests/tags": () =>
        json({
          result: "TAG_SET_CHANGED",
          preferences: null,
          activeTagSetId: 2,
        }),
    });
    const user = userEvent.setup();
    await section("구독할 주제");

    await user.click(
      screen.getByRole("button", { name: "주제 · 지역 · 형식 저장" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "그사이 태그 체계가 바뀌었습니다. 새 체계로 다시 읽었으니 확인하고 다시 저장해 주세요.",
    );
    expect(reads).toBe(2);
  });

  it("소스 이름을 보이고 고른 소스를 빼도록 저장한다", async () => {
    const { server } = renderInterests({
      "PUT /api/interests/sources": () =>
        json({ result: "SAVED", excluded: [5] }),
    });
    const user = userEvent.setup();
    const sources = await section("뺄 소스");

    expect(
      await sources.findByRole("checkbox", { name: "이름 없는 소스 #3" }),
    ).toBeInTheDocument();
    await user.click(sources.getByRole("checkbox", { name: "알림일보" }));
    await user.click(sources.getByRole("button", { name: "뺄 소스 저장" }));

    expect(await sources.findByRole("status")).toHaveTextContent(
      "뺄 소스를 저장했습니다.",
    );
    expect(bodiesOf(server, "PUT", "/api/interests/sources")).toEqual([
      { excluded: [5] },
    ]);
  });

  it("일정 · 리포트를 저장한다", async () => {
    const { server } = renderInterests({
      "PUT /api/interests/settings": () =>
        json({ result: "SAVED", settings: SETTINGS }),
    });
    const user = userEvent.setup();
    const schedule = await section("일정 · 변화 리포트");

    expect(await schedule.findByLabelText("시간대")).toHaveValue("Asia/Seoul");
    expect(schedule.getByLabelText("다이제스트를 만드는 시각")).toHaveValue(
      "07:00",
    );
    expect(
      schedule.queryByLabelText("자유 텍스트 관심사"),
    ).not.toBeInTheDocument();
    await user.selectOptions(
      schedule.getByLabelText("주간 리포트 요일"),
      "금요일",
    );
    await user.click(
      schedule.getByRole("checkbox", { name: "월간 변화 리포트 받기" }),
    );
    await user.click(
      schedule.getByRole("button", { name: "일정 · 리포트 저장" }),
    );

    expect(await schedule.findByRole("status")).toHaveTextContent(
      "일정 · 리포트를 저장했습니다.",
    );
    expect(bodiesOf(server, "PUT", "/api/interests/settings")).toEqual([
      {
        timezone: "Asia/Seoul",
        digestTime: "07:00",
        weeklyReport: true,
        weeklyReportDay: "FRIDAY",
        monthlyReport: false,
      },
    ]);
  });

  it.each([
    [
      "UNKNOWN_TIMEZONE",
      { result: "UNKNOWN_TIMEZONE", settings: null },
      "모르는 시간대입니다. Asia/Seoul 같은 시간대 이름을 적어 주세요.",
    ],
  ] as const)(
    "설정 저장이 %s 이면 저장하지 않은 까닭을 알린다",
    async (_result, response, text) => {
      renderInterests({ "PUT /api/interests/settings": () => json(response) });
      const user = userEvent.setup();
      const schedule = await section("일정 · 변화 리포트");

      await user.click(
        await schedule.findByRole("button", {
          name: "일정 · 리포트 저장",
        }),
      );

      expect(await schedule.findByRole("alert")).toHaveTextContent(text);
    },
  );

  it("분량은 서버가 준 단계 값과 범위로 그리고, 직접 입력은 그 단계를 고를 때만 연다", async () => {
    renderInterests({});
    const volume = await section("다이제스트 분량");

    expect(
      await volume.findByRole("radio", { name: "보통 (40건)" }),
    ).toBeChecked();
    expect(
      volume.getByRole("radio", { name: "적게 (20건)" }),
    ).not.toBeChecked();
    expect(
      volume.getByRole("radio", { name: "많이 (60건)" }),
    ).not.toBeChecked();
    expect(
      volume.getByLabelText("직접 입력한 후보 수 (10 ~ 100건)"),
    ).toBeDisabled();
    expect(volume.getByLabelText("큰 화제 수 (0 ~ 10개)")).toHaveValue(3);
    expect(volume.getByRole("radio", { name: "보통" })).toBeChecked();
    expect(volume.getByRole("radio", { name: "짧게" })).not.toBeChecked();
    expect(volume.getByRole("radio", { name: "자세히" })).not.toBeChecked();
    // 최대 후보 수 · 큰 화제는 사건 단위로 안내한다
    expect(
      volume.getByText(/후보는 다이제스트에 넣을 사건/),
    ).toBeInTheDocument();
    expect(
      volume.getByText(/큰 화제 사건은 최대 후보 수의 절반을 넘지 않습니다/),
    ).toBeInTheDocument();
  });

  it("요약 길이를 골라 분량과 함께 저장한다", async () => {
    let current = VOLUME;
    const { server } = renderInterests({
      "GET /api/interests/volume": () => json(current),
      "PUT /api/interests/volume": () => {
        current = { ...VOLUME, summaryLength: "DETAILED" };
        return json({ result: "SAVED", volume: current, range: null });
      },
    });
    const user = userEvent.setup();
    const volume = await section("다이제스트 분량");
    const volumeReads = () =>
      server.requests.filter(
        (r) => r.method === "GET" && r.path === "/api/interests/volume",
      ).length;

    await user.click(await volume.findByRole("radio", { name: "자세히" }));
    const readsBeforeSave = volumeReads();
    await user.click(volume.getByRole("button", { name: "분량 저장" }));

    expect(await volume.findByRole("status")).toHaveTextContent(
      "다이제스트 분량을 저장했습니다.",
    );
    // 저장 뒤 분량을 다시 읽는다 — 아래 선택 상태는 다시 읽은 값이다
    await waitFor(() => expect(volumeReads()).toBeGreaterThan(readsBeforeSave));
    expect(bodiesOf(server, "PUT", "/api/interests/volume")).toEqual([
      {
        volume: "NORMAL",
        customCount: null,
        bigSlots: 3,
        summaryLength: "DETAILED",
      },
    ]);
    expect(await volume.findByRole("radio", { name: "자세히" })).toBeChecked();
  });

  it("추론 강도는 키 · 고른 모델이 없어도 고르고 저장한다", async () => {
    let current = NO_KEY_MODEL;
    const { server } = renderInterests({
      "GET /api/interests/model": () => json(current),
      "PUT /api/interests/effort": () => {
        current = { ...NO_KEY_MODEL, effort: "HIGH" };
        return json(current);
      },
    });
    const user = userEvent.setup();
    const model = await section("모델");

    const modelReads = () =>
      server.requests.filter(
        (r) => r.method === "GET" && r.path === "/api/interests/model",
      ).length;

    expect(await model.findByRole("radio", { name: "보통" })).toBeChecked();
    await user.click(model.getByRole("radio", { name: "높음" }));
    const readsBeforeSave = modelReads();
    await user.click(model.getByRole("button", { name: "추론 강도 저장" }));

    expect(await model.findByRole("status")).toHaveTextContent(
      "추론 강도를 저장했습니다.",
    );
    // 저장 뒤 모델 조회를 다시 읽는다 — 아래 선택 상태는 다시 읽은 값이다
    await waitFor(() => expect(modelReads()).toBeGreaterThan(readsBeforeSave));
    expect(bodiesOf(server, "PUT", "/api/interests/effort")).toEqual([
      { effort: "HIGH" },
    ]);
    expect(await model.findByRole("radio", { name: "높음" })).toBeChecked();
    // 대조 — 키가 없어 모델은 고르지 못한다는 안내는 그대로
    expect(
      model.getByRole("link", { name: "LLM 키 등록하기" }),
    ).toBeInTheDocument();
  });

  it("직접 입력한 수와 큰 화제 수를 저장하고 알린다", async () => {
    let current = VOLUME;
    const { server } = renderInterests({
      "GET /api/interests/volume": () => json(current),
      "PUT /api/interests/volume": () => {
        current = { ...VOLUME, volume: "CUSTOM", customCount: 45, bigSlots: 0 };
        return json({ result: "SAVED", volume: current, range: null });
      },
    });
    const user = userEvent.setup();
    const volume = await section("다이제스트 분량");

    await user.click(await volume.findByRole("radio", { name: "직접 입력" }));
    const count = volume.getByLabelText("직접 입력한 후보 수 (10 ~ 100건)");
    expect(count).toBeEnabled();
    await user.clear(count);
    await user.type(count, "45");
    const bigSlots = volume.getByLabelText("큰 화제 수 (0 ~ 10개)");
    await user.clear(bigSlots);
    await user.type(bigSlots, "0");
    await user.click(volume.getByRole("button", { name: "분량 저장" }));

    expect(await volume.findByRole("status")).toHaveTextContent(
      "다이제스트 분량을 저장했습니다.",
    );
    expect(bodiesOf(server, "PUT", "/api/interests/volume")).toEqual([
      {
        volume: "CUSTOM",
        customCount: 45,
        bigSlots: 0,
        summaryLength: "NORMAL",
      },
    ]);
    expect(
      await volume.findByLabelText("직접 입력한 후보 수 (10 ~ 100건)"),
    ).toHaveValue(45);
  });

  it("단계를 고르면 직접 입력한 수를 보내지 않는다", async () => {
    const { server } = renderInterests({
      "PUT /api/interests/volume": () =>
        json({
          result: "SAVED",
          volume: { ...VOLUME, volume: "MANY" },
          range: null,
        }),
    });
    const user = userEvent.setup();
    const volume = await section("다이제스트 분량");

    await user.click(await volume.findByRole("radio", { name: "많이 (60건)" }));
    await user.click(volume.getByRole("button", { name: "분량 저장" }));

    expect(await volume.findByRole("status")).toBeInTheDocument();
    expect(bodiesOf(server, "PUT", "/api/interests/volume")).toEqual([
      {
        volume: "MANY",
        customCount: null,
        bigSlots: 3,
        summaryLength: "NORMAL",
      },
    ]);
  });

  // 저장 결과의 범위는 조회한 범위(10 ~ 100 · 0 ~ 10)와 다르게 둔다 — 안내가 저장 결과의 범위를 읽는지 가린다
  it.each([
    [
      "COUNT_OUT_OF_RANGE",
      {
        result: "COUNT_OUT_OF_RANGE",
        volume: null,
        range: { min: 20, max: 120 },
      },
      "직접 입력한 후보 수는 20 ~ 120건이어야 합니다.",
    ],
    [
      "BIG_SLOTS_OUT_OF_RANGE",
      {
        result: "BIG_SLOTS_OUT_OF_RANGE",
        volume: null,
        range: { min: 1, max: 8 },
      },
      "큰 화제 수는 1 ~ 8개여야 합니다.",
    ],
  ] as const)(
    "분량 저장이 %s 이면 받는 범위를 알린다",
    async (_result, response, text) => {
      renderInterests({ "PUT /api/interests/volume": () => json(response) });
      const user = userEvent.setup();
      const volume = await section("다이제스트 분량");

      await user.click(
        await volume.findByRole("button", { name: "분량 저장" }),
      );

      expect(await volume.findByRole("alert")).toHaveTextContent(text);
    },
  );

  it("키가 없으면 모델 대신 키 등록을 안내한다", async () => {
    renderInterests({});

    const model = await section("모델");
    expect(
      await model.findByRole("link", { name: "LLM 키 등록하기" }),
    ).toHaveAttribute("href", "/keys");
    expect(model.queryByLabelText("쓸 모델")).not.toBeInTheDocument();
  });

  it("쓸 수 있는 키가 없어 쓸 모델이 없으면 다이제스트를 만들지 못한다고 알린다", async () => {
    renderInterests({
      "GET /api/interests/model": () =>
        json({
          choosable: [{ provider: "ANTHROPIC", id: "claude-haiku-5-5" }],
          chosen: { provider: "ANTHROPIC", id: "claude-haiku-5-5" },
          effective: null,
          reason: "KEY_INVALID",
          effort: "MEDIUM",
          efforts: ["LOW", "MEDIUM", "HIGH"],
        } satisfies InterestModel),
    });

    const model = await section("모델");
    expect(await model.findByRole("note")).toHaveTextContent(
      "고른 모델의 공급자 키가 무효입니다. 지금은 쓸 모델이 없어 다이제스트를 만들지 못합니다.",
    );
  });

  it("고른 모델을 쓸 수 없으면 까닭과 지금 쓸 모델을 보이고, 고른 모델을 저장한다", async () => {
    const { server } = renderInterests({
      "GET /api/interests/model": () =>
        json({
          choosable: [
            { provider: "OPENAI", id: "gpt-5.5" },
            { provider: "OPENAI", id: "gpt-5.5-mini" },
          ],
          chosen: { provider: "ANTHROPIC", id: "claude-opus-5-5" },
          effective: { provider: "OPENAI", id: "gpt-5.5" },
          reason: "NO_KEY",
          effort: "MEDIUM",
          efforts: ["LOW", "MEDIUM", "HIGH"],
        } satisfies InterestModel),
      "PUT /api/interests/model": () => json({ result: "SAVED", model: null }),
    });
    const user = userEvent.setup();
    const model = await section("모델");

    expect(await model.findByRole("note")).toHaveTextContent(
      "고른 모델의 공급자에 등록한 키가 없습니다. 지금은 OpenAI의 gpt-5.5 모델을 씁니다.",
    );
    await user.selectOptions(
      model.getByLabelText("쓸 모델"),
      "OpenAI · gpt-5.5-mini",
    );
    await user.click(model.getByRole("button", { name: "모델 저장" }));

    expect(await model.findByRole("status")).toHaveTextContent(
      "모델을 저장했습니다.",
    );
    expect(bodiesOf(server, "PUT", "/api/interests/model")).toEqual([
      { provider: "OPENAI", modelId: "gpt-5.5-mini" },
    ]);
  });
});
