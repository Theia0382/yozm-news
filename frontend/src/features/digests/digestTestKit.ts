// 다이제스트 화면 시험의 응답 만들기 — 기본값에 바꿀 칸만 덮어쓴다.

import type {
  DigestAttempt,
  DigestCitation,
  DigestDay,
  DigestHome,
  DigestRequestStatus,
  DigestSection,
  ShownDigest,
} from "@/features/digests/digestQueries";

export function citation(
  articleId: number,
  overrides: Partial<DigestCitation> = {},
): DigestCitation {
  return {
    articleId,
    article: {
      title: `기사 ${articleId}`,
      sources: ["한국일보"],
      publishedAt: "2026-11-17T22:30:00Z",
      url: `https://news.example/${articleId}`,
    },
    note: null,
    reason: "SUBSCRIBED",
    ...overrides,
  };
}

export const SECTIONS: DigestSection[] = [
  {
    kind: "OVERVIEW",
    title: null,
    body: "오늘의 흐름 <b>굵게</b>",
    citations: [],
  },
  {
    kind: "TOP_STORY",
    title: "주요 뉴스 제목",
    body: "주요 뉴스 요약",
    citations: [
      citation(1),
      citation(2, { reason: "MAJOR_STORYLINE" }),
      citation(3, { reason: "INTEREST" }),
    ],
  },
  {
    kind: "MORE_HEADLINES",
    title: null,
    body: null,
    citations: [citation(4, { note: "한 줄 코멘트" })],
  },
];

export function shown(overrides: Partial<ShownDigest> = {}): ShownDigest {
  return {
    digestId: 11,
    periodStart: "2026-11-16T22:00:00Z",
    periodEnd: "2026-11-17T22:00:00Z",
    provider: "openai",
    model: "gpt-test",
    candidateCount: 30,
    inputTokens: 1200,
    outputTokens: 300,
    completedAt: "2026-11-17T22:02:00Z",
    sections: SECTIONS,
    ...overrides,
  };
}

export function attempt(overrides: Partial<DigestAttempt> = {}): DigestAttempt {
  return {
    digestId: 11,
    status: "READY",
    failure: null,
    cause: null,
    completedAt: "2026-11-17T22:02:00Z",
    ...overrides,
  };
}

export function day(overrides: Partial<DigestDay> = {}): DigestDay {
  return {
    date: "2026-11-18",
    previousDate: "2026-11-17",
    nextDate: null,
    shown: shown(),
    latest: attempt(),
    usage: { attempts: 1, inputTokens: 1200, outputTokens: 300 },
    ...overrides,
  };
}

export function status(
  overrides: Partial<DigestRequestStatus> = {},
): DigestRequestStatus {
  return {
    today: "2026-11-18",
    timezone: "Asia/Seoul",
    usableModel: true,
    inProgress: false,
    remainingToday: 3,
    resetsAt: "2026-11-18T15:00:00Z",
    targetDate: "2026-11-18",
    regenerates: true,
    scheduledAt: null,
    unresolvedFailure: null,
    ...overrides,
  };
}

export function home(overrides: Partial<DigestHome> = {}): DigestHome {
  return { day: day(), status: status(), ...overrides };
}
