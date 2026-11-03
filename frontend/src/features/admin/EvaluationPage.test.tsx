import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createAdminClient, type Schemas } from "@/features/admin/adminClient";
import { AdminSessionContext } from "@/features/admin/adminSession";
import {
  fakeServer,
  type Handler,
  json,
  problem,
  type RecordedRequest,
  TEST_BASE_URL,
} from "@/features/admin/adminTestServer";
import { EvaluationPage } from "@/features/admin/EvaluationPage";

type Overview = Schemas["EvaluationOverviewResponse"];

const COVERAGE_PATH = "GET /api/admin/topic-coverage";
const OVERVIEW_PATH = "GET /api/admin/evaluation-overview";
const QUEUE_PATH = "GET /api/admin/evaluation-queue";
const TAG_SETS_PATH = "GET /api/admin/tag-sets";

const FROM = "2026-10-27T15:00:00Z";
const TO = "2026-11-03T15:00:00Z";

const COVERAGE: Schemas["TopicCoverageResponse"] = {
  activeVersion: 7,
  from: FROM,
  to: TO,
  articles: 1234,
  withoutActiveTopic: 5,
  categories: [
    {
      key: "politics",
      name: "정치",
      articles: 3,
      subtopics: [
        { key: "politics.executive", name: "대통령실·행정부", articles: 3 },
        { key: "politics.legislature", name: "국회·정당·선거", articles: 2 },
      ],
    },
  ],
  maxDays: 31,
};

const share = (count: number, ratio: number | null) => ({ count, ratio });

const OVERVIEW: Overview = {
  version: 7,
  from: FROM,
  to: TO,
  evaluations: 4,
  topicStatus: {
    tagged: share(2, 0.5),
    lowConfidence: share(1, 0.25),
    unclassified: share(1, 0.25),
  },
  topics: [
    {
      key: "politics.executive",
      name: "대통령실·행정부",
      share: share(2, 0.5),
    },
  ],
  topicCategories: [{ key: "politics", name: "정치", share: share(2, 0.5) }],
  regions: [{ key: "domestic", name: "국내", share: share(1, 0.25) }],
  formats: [{ key: "straight", name: "스트레이트", share: share(4, 1) }],
  topicsPerArticle: [
    { tags: 0, orMore: false, share: share(1, 0.25) },
    { tags: 5, orMore: true, share: share(3, 0.75) },
  ],
  regionsPerArticle: [{ tags: 0, orMore: false, share: share(4, 1) }],
  topicProbabilities: {
    storeThreshold: 0.15,
    storeThresholdChanged: true,
    buckets: [
      { lower: 0, upper: 0.1, upperInclusive: false, count: 0, stored: "NONE" },
      {
        lower: 0.1,
        upper: 0.2,
        upperInclusive: false,
        count: 2,
        stored: "PART",
      },
      { lower: 0.9, upper: 1, upperInclusive: true, count: 7, stored: "ALL" },
    ],
  },
  regionProbabilities: {
    storeThreshold: 0.1,
    storeThresholdChanged: false,
    buckets: [],
  },
  formatProbabilities: {
    storeThreshold: null,
    storeThresholdChanged: false,
    buckets: [],
  },
  importance: [
    {
      lower: 4.5,
      upper: 5,
      upperInclusive: true,
      share: share(3, 0.75),
    },
  ],
  importanceMissing: 1,
  inputTokens: {
    mean: 1425.5,
    min: 900,
    max: 2100,
    buckets: [{ lower: 750, upper: 1000, count: 1 }],
  },
};

const EMPTY_OVERVIEW: Overview = {
  ...OVERVIEW,
  evaluations: 0,
  topicStatus: {
    tagged: share(0, null),
    lowConfidence: share(0, null),
    unclassified: share(0, null),
  },
  inputTokens: { mean: null, min: null, max: null, buckets: [] },
};

const TAG_SETS: Schemas["TagSetListResponse"] = {
  versions: [
    {
      id: 7,
      number: 2,
      active: true,
      createdBy: "OPERATOR",
      createdAt: "2026-11-02T03:00:00Z",
    },
    {
      id: 1,
      number: 1,
      active: false,
      createdBy: "SYSTEM",
      createdAt: "2026-11-01T00:00:00Z",
    },
  ],
  maxReevaluationDays: 30,
};

function renderPage(handlers: Partial<Record<string, Handler>> = {}) {
  const server = fakeServer({
    [COVERAGE_PATH]: () => json(COVERAGE),
    [OVERVIEW_PATH]: () => json(OVERVIEW),
    [QUEUE_PATH]: () => json({ waiting: 12, rejected: 3, exhausted: 4 }),
    [TAG_SETS_PATH]: () => json(TAG_SETS),
    ...(handlers as Record<string, Handler>),
  });
  const client = createAdminClient({
    baseUrl: TEST_BASE_URL,
    fetch: server.fetch,
    token: () => "token",
  });
  const router = createMemoryRouter([
    { path: "/", element: <EvaluationPage /> },
  ]);
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <AdminSessionContext.Provider value={client}>
        <RouterProvider router={router} />
      </AdminSessionContext.Provider>
    </QueryClientProvider>,
  );
  return server;
}

function requestsTo(server: ReturnType<typeof fakeServer>, key: string) {
  return server.requests.filter(
    (request) => `${request.method} ${request.path}` === key,
  );
}

function query(request: RecordedRequest | undefined): Record<string, string> {
  return Object.fromEntries(new URLSearchParams(request?.search ?? ""));
}

function bars(name: string): HTMLElement {
  return screen.getByRole("list", { name });
}

describe("EvaluationPage", () => {
  beforeEach(() => {
    // 2026-11-03 12:00 Asia/Seoul(시험 문맥의 TZ) — 날짜만 고정하고 타이머는 그대로 둔다
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-11-03T03:00:00Z"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("오늘을 포함한 7일 · 브라우저 시간대 · 활성 버전으로 처음 부른다", async () => {
    const server = renderPage();

    expect(await screen.findByText("정치 — 중분류 2개")).toBeInTheDocument();
    expect(await screen.findByText("평가 4건")).toBeInTheDocument();
    expect(screen.getByLabelText("시작일")).toHaveValue("2026-10-28");
    expect(screen.getByLabelText("종료일(포함)")).toHaveValue("2026-11-03");
    expect(query(requestsTo(server, COVERAGE_PATH)[0])).toEqual({
      from: "2026-10-28",
      to: "2026-11-03",
      zone: "Asia/Seoul",
    });
    expect(query(requestsTo(server, OVERVIEW_PATH)[0])).toEqual({
      version: "7",
      from: "2026-10-28",
      to: "2026-11-03",
      zone: "Asia/Seoul",
    });
    expect(screen.getByLabelText("태그 체계 버전")).toHaveValue("7");
  });

  it("집계 기간을 시각과 시간대로, 기준 축과 함께 적는다", async () => {
    renderPage();

    expect(
      await screen.findByText(
        "집계 기간: 2026. 10. 28. 오전 12:00 GMT+9 ~ 2026. 11. 04. 오전 12:00 GMT+9 (첫 평가 시각 기준)",
      ),
    ).toBeInTheDocument();
    expect(
      await screen.findByText(
        "집계 기간: 2026. 10. 28. 오전 12:00 GMT+9 ~ 2026. 11. 04. 오전 12:00 GMT+9 (평가 시각 기준)",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText("날짜는 이 브라우저의 시간대(Asia/Seoul) 자정 기준이다"),
    ).toBeInTheDocument();
  });

  it("날짜를 바꾸면 보도량 · 품질 지표를 그 기간으로 다시 부르고 대기 건수는 다시 부르지 않는다", async () => {
    const server = renderPage();
    await screen.findByText("평가 4건");

    fireEvent.change(screen.getByLabelText("시작일"), {
      target: { value: "2026-10-30" },
    });

    await vi.waitFor(() =>
      expect(requestsTo(server, OVERVIEW_PATH)).toHaveLength(2),
    );
    expect(query(requestsTo(server, COVERAGE_PATH)[1])).toMatchObject({
      from: "2026-10-30",
      to: "2026-11-03",
    });
    expect(query(requestsTo(server, OVERVIEW_PATH)[1])).toMatchObject({
      version: "7",
      from: "2026-10-30",
    });
    expect(requestsTo(server, QUEUE_PATH)).toHaveLength(1);
  });

  it("버전을 바꾸면 품질 지표만 그 버전으로 다시 부른다", async () => {
    const server = renderPage();
    await screen.findByText("평가 4건");

    await userEvent.selectOptions(screen.getByLabelText("태그 체계 버전"), "1");

    await vi.waitFor(() =>
      expect(requestsTo(server, OVERVIEW_PATH)).toHaveLength(2),
    );
    expect(query(requestsTo(server, OVERVIEW_PATH)[1])).toMatchObject({
      version: "1",
    });
    expect(requestsTo(server, COVERAGE_PATH)).toHaveLength(1);
    expect(requestsTo(server, QUEUE_PATH)).toHaveLength(1);
    expect(
      screen.getByRole("option", { name: "버전 2 (활성)" }),
    ).toBeInTheDocument();
  });

  it("막대 줄마다 서버가 낸 수와 비율을 글자로 보인다", async () => {
    renderPage();
    await screen.findByText("평가 4건");

    const statuses = within(bars("주제 판정별 평가 수")).getAllByRole(
      "listitem",
    );
    expect(statuses[0]).toHaveTextContent(
      "태그 기준을 넘은 주제 있음2 · 50.0%",
    );
    expect(statuses[1]).toHaveTextContent("low_confidence1 · 25.0%");
    expect(statuses[2]).toHaveTextContent("미분류1 · 25.0%");
    expect(bars("주제 태그 수별 평가 수")).toHaveTextContent(
      "5개 이상3 · 75.0%",
    );
    expect(bars("형식(대표 형식)")).toHaveTextContent("스트레이트4 · 100.0%");
    expect(bars("중요도 기대값 칸별 평가 수")).toHaveTextContent(
      "4.5 이상 5.0 이하3 · 75.0%",
    );
    expect(screen.getByText("기대값 없음 1건")).toBeInTheDocument();
    expect(
      screen.getByText("평균 1,425.5 · 최소 900 · 최대 2,100"),
    ).toBeInTheDocument();
    expect(bars("입력 토큰 칸별 평가 수")).toHaveTextContent(
      "750 이상 1,000 미만1",
    );
  });

  it("저장 기준 아래 칸은 저장하지 않음, 걸친 칸은 일부 저장, 기준이 바뀐 버전은 알린다", async () => {
    renderPage();
    await screen.findByText("평가 4건");

    const buckets = within(bars("주제 확률 칸별 수")).getAllByRole("listitem");
    expect(buckets[0]).toHaveTextContent("0.0 이상 0.1 미만0저장하지 않음");
    expect(buckets[1]).toHaveTextContent("0.1 이상 0.2 미만2일부 저장");
    expect(buckets[2]).toHaveTextContent("0.9 이상 1.0 이하7");
    expect(buckets[2]).not.toHaveTextContent("저장");
    expect(screen.getByText("주제 — 지금 저장 기준 0.15")).toBeInTheDocument();
    expect(screen.getByText("지역 — 지금 저장 기준 0.1")).toBeInTheDocument();
    // 형식은 저장 기준이 없어 기준 문구를 붙이지 않는다
    expect(screen.getByText("형식(대표 형식의 확률)")).toBeInTheDocument();
    expect(
      screen.getAllByText(/이 버전에서 저장 기준이 바뀐 적이 있다/),
    ).toHaveLength(1);
  });

  it("평가가 없는 기간은 비율 · 평균을 없음으로 보인다", async () => {
    renderPage({ [OVERVIEW_PATH]: () => json(EMPTY_OVERVIEW) });

    expect(
      await screen.findByText(
        "평가 0건 — 이 기간에 이 버전의 평가가 없다(비율 · 평균은 없음)",
      ),
    ).toBeInTheDocument();
    expect(bars("주제 판정별 평가 수")).toHaveTextContent("미분류0 · 없음");
    expect(
      screen.getByText("평균 없음 · 최소 없음 · 최대 없음"),
    ).toBeInTheDocument();
  });

  it("대분류를 펼치면 중분류별 기사 수를 보고, 대분류의 수는 하위의 합이 아니다", async () => {
    renderPage();
    const summary = await screen.findByText("정치 — 중분류 2개");
    const details = summary.closest("details");
    expect(details).not.toHaveAttribute("open");

    await userEvent.click(summary);

    expect(details).toHaveAttribute("open");
    const subtopics = within(bars("정치 중분류별 기사 수")).getAllByRole(
      "listitem",
    );
    expect(subtopics[0]).toHaveTextContent("대통령실·행정부3");
    expect(subtopics[1]).toHaveTextContent("국회·정당·선거2");
    expect(bars("대분류별 기사 수")).toHaveTextContent("정치3");
    expect(
      screen.getByText("기사 1,234건 · 활성 주제에 이어지지 않은 기사 5건"),
    ).toBeInTheDocument();
  });

  it("지금의 대기 · 실패 건수를 보인다", async () => {
    renderPage();

    const queue = await screen.findByRole("region", { name: "평가 대기" });
    await within(queue).findByText("12");
    expect(queue).toHaveTextContent("대기 중12");
    expect(queue).toHaveTextContent("실패 — 기사 거부3");
    expect(queue).toHaveTextContent("실패 — 재시도 소진4");
  });

  it("서버의 400 문구를 보이고, 상한을 넘거나 끝이 앞서면 보내기 전에 안내한다", async () => {
    const server = renderPage();
    await screen.findByText("평가 4건");
    server.handlers[COVERAGE_PATH] = () =>
      problem(400, "기간은 1일에서 31일까지다");
    server.handlers[OVERVIEW_PATH] = () =>
      problem(400, "기간은 1일에서 31일까지다");

    fireEvent.change(screen.getByLabelText("시작일"), {
      target: { value: "2026-10-01" },
    });

    expect(
      await screen.findByText("기간은 31일까지다 — 지금 34일"),
    ).toBeInTheDocument();
    expect(await screen.findAllByRole("alert")).toHaveLength(2);
    for (const alert of screen.getAllByRole("alert")) {
      expect(alert).toHaveTextContent("기간은 1일에서 31일까지다");
    }

    fireEvent.change(screen.getByLabelText("시작일"), {
      target: { value: "2026-11-04" },
    });
    expect(
      await screen.findByText("종료일이 시작일보다 앞선다"),
    ).toBeInTheDocument();
  });
});
