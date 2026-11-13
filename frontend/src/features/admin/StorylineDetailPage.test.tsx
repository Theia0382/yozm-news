import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { json, problem } from "@/test/fakeServer";
import type { Schemas } from "@/features/admin/adminClient";
import { ARTICLE } from "@/features/admin/eventTestKit";
import { renderAdminRoutes } from "@/features/admin/reviewTestKit";
import { StorylineDetailPage } from "@/features/admin/StorylineDetailPage";

const LINKING = { windowSeconds: 259200, threshold: 0.62 };

const RELATION = {
  probability: 0.6,
  oneMatter: 0.2,
  minEvents: 3,
  candidateScore: 0.88,
  windowSeconds: 1209600,
};

const T = "2026-11-05T00:00:00Z";

function judgement(
  id: number,
  firstId: number,
  secondId: number,
  reading: Schemas["RelationJudgementResponse"]["reading"],
  message: string | null,
): Schemas["RelationJudgementResponse"] {
  const read = reading === "READ";
  return {
    id,
    firstId,
    secondId,
    modelVersion: "jev-1.14.0",
    wordingVersion: "relation-1",
    inputTokens: 1924,
    judgedAt: T,
    reading,
    none: read ? 0.1 : null,
    secondPartOfFirst: read ? 0.7 : null,
    firstPartOfSecond: read ? 0.05 : null,
    firstLedToSecond: read ? 0.1 : null,
    secondLedToFirst: read ? 0.05 : null,
    firstOneMatter: read ? 0.8 : null,
    secondOneMatter: read ? 0.6 : null,
    message,
  };
}

function asked(
  otherId: number,
  outcome: Schemas["StorylineAskedResponse"]["outcome"],
  judgementId: number | null,
  found: Schemas["RelationJudgementResponse"] | null,
): Schemas["StorylineAskedResponse"] {
  return {
    otherId,
    smallerEvents: 3,
    outcome,
    askedAt: T,
    criteria: RELATION,
    judgementId,
    judgement: found,
  };
}

const DETAIL: Schemas["StorylineDetailResponse"] = {
  requestedId: 7,
  id: 7,
  state: "ACTIVE",
  parentId: 2,
  eventCount: 3,
  articleCount: 12,
  firstArticleAt: "2026-11-01T00:00:00Z",
  lastArticleAt: T,
  createdAt: "2026-11-01T01:00:00Z",
  representativeArticleId: 41,
  representative: ARTICLE,
  events: [
    {
      id: 70,
      status: "OPEN",
      articleCount: 4,
      firstArticleAt: T,
      lastArticleAt: T,
      direct: true,
      representativeArticleId: 41,
      representative: ARTICLE,
      memberships: [
        {
          storylineId: 7,
          linkedBy: "RULE",
          score: 0.81,
          cosine: 0.77,
          keyTermOverlap: 0.4,
          criteria: LINKING,
          linkedAt: T,
        },
      ],
    },
    {
      id: 85,
      status: "CLOSED",
      articleCount: 1,
      firstArticleAt: T,
      lastArticleAt: T,
      direct: false,
      representativeArticleId: null,
      representative: null,
      memberships: [
        {
          storylineId: 8,
          linkedBy: "PAIR",
          score: 0.7,
          cosine: 0.66,
          keyTermOverlap: 0.2,
          criteria: LINKING,
          linkedAt: T,
        },
        {
          storylineId: 7,
          linkedBy: "MERGE",
          score: null,
          cosine: null,
          keyTermOverlap: null,
          criteria: null,
          linkedAt: T,
        },
      ],
    },
  ],
  relations: [
    {
      kind: "CONTAINS",
      sourceId: 7,
      targetId: 8,
      probability: 0.7,
      linkedAt: T,
      criteria: RELATION,
      judgementId: 41,
      judgement: judgement(41, 7, 8, "READ", null),
    },
    {
      kind: "CONTAINS",
      sourceId: 2,
      targetId: 7,
      probability: 0.66,
      linkedAt: T,
      criteria: RELATION,
      judgementId: 40,
      judgement: null,
    },
    {
      kind: "FOLLOWS",
      sourceId: 7,
      targetId: 11,
      probability: 0.65,
      linkedAt: T,
      criteria: RELATION,
      judgementId: 42,
      // 판정을 물을 때의 앞은 11 이다 — 관계의 앞(7)과 다르다
      judgement: judgement(42, 11, 7, "READ", null),
    },
    {
      kind: "FOLLOWS",
      sourceId: 5,
      targetId: 7,
      probability: 0.62,
      linkedAt: T,
      criteria: RELATION,
      judgementId: 43,
      judgement: judgement(43, 5, 7, "READ", null),
    },
  ],
  asked: [
    asked(12, "NOTHING_TO_ASK", null, null),
    asked(
      13,
      "LOW_PROBABILITY",
      44,
      judgement(44, 13, 7, "OTHER_EVALUATOR", "다른 평가기 — fake"),
    ),
    asked(14, "REJECTED", null, null),
    asked(
      15,
      "CYCLE",
      45,
      judgement(45, 7, 15, "NO_EVALUATOR", "평가기가 없다"),
    ),
    asked(16, "NOT_ONE_MATTER", 46, null),
  ],
};

function renderPage(id: number, response: () => Response) {
  return renderAdminRoutes(
    [{ path: "/admin/storylines/:id", element: <StorylineDetailPage /> }],
    `/admin/storylines/${id}`,
    { [`GET /api/admin/storylines/${id}`]: response },
  ).server;
}

function cells(table: HTMLElement, row: number) {
  return within(within(table).getAllByRole("row")[row]!).getAllByRole("cell");
}

describe("StorylineDetailPage", () => {
  it("값과 상위 · 대표 기사를 보인다", async () => {
    renderPage(7, () => json(DETAIL));

    expect(
      await screen.findByRole("heading", { name: "스토리라인 7" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("status")).toBeNull();
    const values = screen.getByLabelText("스토리라인 값");
    expect(values).toHaveTextContent("진행");
    expect(values).toHaveTextContent("국회 본회의 통과");
    expect(
      within(values).getByRole("link", { name: "스토리라인 2" }),
    ).toHaveAttribute("href", "/admin/storylines/2");
  });

  it("사건마다 직접 · 하위에서와 그 사건을 직접 가진 스토리라인의 소속 근거를 보인다", async () => {
    renderPage(7, () => json(DETAIL));

    const table = await screen.findByRole("table", {
      name: "스토리라인의 사건",
    });
    const first = cells(table, 1);
    expect(
      within(first[0]!).getByRole("link", { name: "사건 70" }),
    ).toHaveAttribute("href", "/admin/events/70");
    expect(first[1]).toHaveTextContent(/^직접$/);
    expect(first[6]).toHaveTextContent(
      /^이 스토리라인 · 붙이기 · 점수 0\.81 \(코사인 0\.77 · 겹침 0\.4\)창 3일 · 기준 점수 0\.62 · /,
    );
    const second = cells(table, 2);
    expect(second[1]).toHaveTextContent(/^하위에서$/);
    const memberships = within(second[6]!).getAllByRole("listitem");
    expect(memberships[0]).toHaveTextContent(/^스토리라인 8 · 짝 · 점수 0\.7 /);
    expect(
      within(memberships[0]!).getByRole("link", { name: "스토리라인 8" }),
    ).toHaveAttribute("href", "/admin/storylines/8");
    // 병합 옮김은 점수 · 기준이 없다
    expect(memberships[1]).toHaveTextContent(/^이 스토리라인 · 병합 옮김/);
    expect(memberships[1]).not.toHaveTextContent("점수");
    expect(memberships[1]).not.toHaveTextContent("기준 점수");
  });

  it("관계를 이 스토리라인 기준 방향 넷으로 적고 판정 원본의 확률을 번호로 보인다", async () => {
    renderPage(7, () => json(DETAIL));

    const table = await screen.findByRole("table", { name: "관계" });
    expect(cells(table, 1)[0]).toHaveTextContent(/^포함함$/);
    expect(cells(table, 1)[1]).toHaveTextContent(/^스토리라인 8$/);
    expect(cells(table, 1)[2]).toHaveTextContent(/^0\.7$/);
    expect(cells(table, 1)[5]).toHaveTextContent(
      "해당 없음 0.1 · 8 ⊂ 7 0.7 · 7 ⊂ 8 0.05 · 7 → 8 0.1 · 8 → 7 0.05 · 한 사안 7 0.8 · 8 0.6",
    );
    expect(cells(table, 2)[0]).toHaveTextContent(/^포함됨$/);
    expect(cells(table, 2)[1]).toHaveTextContent(/^스토리라인 2$/);
    expect(cells(table, 2)[5]).toHaveTextContent(/^원본 없음$/);
    expect(cells(table, 3)[0]).toHaveTextContent(/^낳음$/);
    // 원본의 확률은 관계의 앞 · 뒤가 아니라 원본의 앞 · 뒤 번호로 적는다
    expect(cells(table, 3)[5]).toHaveTextContent(
      "해당 없음 0.1 · 7 ⊂ 11 0.7 · 11 ⊂ 7 0.05 · 11 → 7 0.1 · 7 → 11 0.05 · 한 사안 11 0.8 · 7 0.6",
    );
    expect(cells(table, 4)[0]).toHaveTextContent(/^이어받음$/);
    expect(cells(table, 4)[1]).toHaveTextContent(/^스토리라인 5$/);
  });

  it("물은 쌍은 마지막 결말과 원본이 없는 까닭을 가려 보인다", async () => {
    renderPage(7, () => json(DETAIL));

    const table = await screen.findByRole("table", { name: "물은 쌍" });
    expect(cells(table, 1)[1]).toHaveTextContent(/^물을 것 없음$/);
    expect(cells(table, 1)[5]).toHaveTextContent(/^원본을 남기지 않는 결말$/);
    expect(cells(table, 2)[1]).toHaveTextContent(/^확률 기준 미달$/);
    expect(cells(table, 2)[5]).toHaveTextContent(
      /다른 평가기 — 다른 평가기 — fake$/,
    );
    // 입력 거부도 원본이 없는 결말이다 — "평가기를 부르지 않음"이 아니다
    expect(cells(table, 3)[1]).toHaveTextContent(/^입력 거부$/);
    expect(cells(table, 3)[5]).toHaveTextContent(/^원본을 남기지 않는 결말$/);
    expect(cells(table, 4)[1]).toHaveTextContent(/^순환$/);
    expect(cells(table, 4)[5]).toHaveTextContent(
      /평가기 없음 — 평가기가 없다$/,
    );
    expect(cells(table, 5)[1]).toHaveTextContent(/^한 사안 기준 미달$/);
    expect(cells(table, 5)[5]).toHaveTextContent(/^원본 없음$/);
    expect(cells(table, 1)[2]).toHaveTextContent(/^3$/);
    expect(cells(table, 1)[4]).toHaveTextContent(
      /^확률 0\.6 · 한 사안 0\.2 · 사건 수 3 이상 · 후보 점수 0\.88 · 창 14일$/,
    );
  });

  it("합쳐진 스토리라인의 ID 로 열면 남는 스토리라인을 보이고 알린다", async () => {
    renderPage(5, () =>
      json({ ...DETAIL, requestedId: 5, relations: [], asked: [] }),
    );

    expect(
      await screen.findByRole("heading", { name: "스토리라인 7" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "합쳐진 스토리라인 5 대신 남는 스토리라인을 보인다",
    );
    expect(screen.getByText("맺은 관계가 없다")).toBeInTheDocument();
    expect(screen.getByText("관계를 물은 상대가 없다")).toBeInTheDocument();
  });

  it("없는 스토리라인은 서버의 설명을 보인다", async () => {
    renderPage(99, () => problem(404, "없는 스토리라인: 99"));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "없는 스토리라인: 99",
    );
  });
});
