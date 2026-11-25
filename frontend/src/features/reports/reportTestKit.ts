// 변화 리포트 화면 시험의 응답 조각 — 목록 · 한 편 시험이 함께 쓴다.

import type {
  ReportDetail,
  ReportStoryline,
  ReportSummary,
} from "@/features/reports/reportQueries";

export const ARTICLE = {
  title: "국회 본회의 통과",
  sources: ["한겨레", "경향신문"],
  publishedAt: "2026-11-17T01:00:00Z",
  url: "https://news.example/41",
};

/** 주간 리포트 한 줄 — 2026-11-16 ~ 11-22, 내용 있음 */
export function summary(
  id: number,
  overrides: Partial<ReportSummary> = {},
): ReportSummary {
  return {
    id,
    kind: "WEEK",
    firstDay: "2026-11-16",
    lastDay: "2026-11-22",
    status: "READY",
    failure: null,
    completedAt: "2026-11-22T15:01:00Z",
    ...overrides,
  };
}

/** 스토리라인 하나의 표시 — 정리 없음, 대표 기사 있음 */
export function storyline(
  storylineId: number,
  overrides: Partial<ReportStoryline> = {},
): ReportStoryline {
  return {
    storylineId,
    title: { ...ARTICLE, title: `흐름 ${storylineId} 대표 기사` },
    userStorylines: [],
    ...overrides,
  };
}

/** 한 편 — 섹션 없음, 직전 없음, 숨김 0 */
export function detail(
  report: ReportSummary,
  overrides: Partial<ReportDetail> = {},
): ReportDetail {
  return {
    report,
    previous: null,
    hiddenSections: 0,
    usage:
      report.status === "SCHEDULED"
        ? null
        : {
            provider: "openai",
            model: "gpt-5.5-mini",
            inputTokens: report.status === "READY" ? 1200 : null,
            outputTokens: report.status === "READY" ? 450 : null,
          },
    sections: [],
    regenerationsLeft: report.status === "FAILED" ? 3 : null,
    ...overrides,
  };
}
