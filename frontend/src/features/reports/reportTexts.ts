// 변화 리포트 화면의 문구 — 한 곳에 둔다(docs/specs/reader.md reader-R15 · reader-R16).

import type {
  InterestShift,
  ReportFailure,
  ReportKind,
  ReportStatus,
  ReportTrend,
} from "@/features/reports/reportQueries";
import type { components } from "@/shared/api/schema";
import { formatDate } from "@/shared/lib/dateTime";

type SectionKind = components["schemas"]["ReportSectionResponse"]["kind"];

/** 목록의 주소 */
export const REPORTS_PATH = "/reports";

/** 한 종류의 목록 주소 */
export function reportsPath(kind: ReportKind): string {
  return `${REPORTS_PATH}?kind=${kind}`;
}

/** 리포트 한 편의 주소 */
export function reportPath(id: number): string {
  return `${REPORTS_PATH}/${id}`;
}

/** 화면 이름 */
export const REPORTS_TITLE = "변화 리포트";

/** 목록 머리의 설명 */
export const REPORTS_INTRO =
  "주간 · 월간으로 추적한 흐름, 구독한 주제의 보도량, 관심이 어떻게 달라졌는지 정리한 리포트입니다. 리포트는 등록한 LLM 키로 만듭니다.";

/** 종류의 이름 */
export const kindLabels = {
  WEEK: "주간",
  MONTH: "월간",
} as const satisfies Record<ReportKind, string>;

/** 리포트 기간 — 첫날 ~ 마지막 날 */
export function periodText(firstDay: string, lastDay: string): string {
  return `${formatDate(firstDay)} ~ ${formatDate(lastDay)}`;
}

/** 한 편의 제목 */
export function reportTitle(
  kind: ReportKind,
  firstDay: string,
  lastDay: string,
): string {
  return `${kindLabels[kind]} 리포트 · ${periodText(firstDay, lastDay)}`;
}

/** 목록에 적는 상태 — 내용이 있으면 적지 않는다 */
export const statusLabels = {
  SCHEDULED: "만드는 중",
  RUNNING: "만드는 중",
  READY: null,
  EMPTY: "보일 변화 없음",
  FAILED: "만들지 못함",
} as const satisfies Record<ReportStatus, string | null>;

/** 한 편이 아직 만들어지는 중일 때 */
export const IN_PROGRESS = "리포트를 만드는 중입니다.";

/** 한 편이 비어 있을 때(재료 없음) */
export const EMPTY_TEXT =
  "이 기간에는 리포트에 담을 변화가 없었습니다 — 추적한 흐름 · 구독한 주제 · 관심 신호가 없으면 비어 있습니다.";

/** 한 편이 실패했을 때의 머리 문장 */
export const FAILED_TITLE = "리포트를 만들지 못했습니다.";

/** 실패 코드마다 무슨 일이 있었는가(ADR-0240 의 꼴 — 실패 메시지는 보이지 않는다) */
export const reportFailureTexts: Record<ReportFailure, string> = {
  UNREADABLE_OUTPUT:
    "모델의 답을 리포트 형식으로 읽지 못했습니다. 한 번 더 물었지만 마찬가지였습니다.",
  OUTPUT_TRUNCATED: "모델의 답이 출력 한도에 닿아 잘렸습니다.",
  OUTPUT_REFUSED: "모델이 리포트 작성을 거절했습니다.",
  KEY_MISSING: "쓸 모델의 공급자에 등록한 LLM 키가 없었습니다.",
  KEY_INVALID: "공급자가 등록한 LLM 키를 받아 주지 않았습니다.",
  PERMISSION_DENIED: "키에 이 모델을 쓸 권한이 없었습니다.",
  QUOTA_EXHAUSTED: "공급자 계정의 잔액이나 지출 한도가 소진됐습니다.",
  PROVIDER_UNAVAILABLE: "공급자가 여러 번 다시 보내도 답하지 못했습니다.",
  PROVIDER_UNEXPECTED: "공급자가 예상하지 못한 답을 했습니다.",
  NO_USABLE_MODEL:
    "쓸 수 있는 모델이 없어 만들기를 시작하지 못했습니다. 유효한 LLM 키와 고른 모델을 확인하세요.",
  INTERRUPTED: "만들기가 정한 시간 안에 끝나지 않아 멈췄습니다.",
};

/** 섹션 종류마다 숫자 표의 이름 — 개요는 표가 없다 */
export const statsTitles = {
  OVERVIEW: null,
  STORYLINES: "추적한 흐름의 보도량",
  COVERAGE: "구독한 주제의 보도량",
  INTERESTS: "관심의 변화",
} as const satisfies Record<SectionKind, string | null>;

/** 보도량 판정의 이름 */
export const trendLabels = {
  NEW: "새로 생김",
  ROSE: "늘었음",
  FELL: "줄었음",
  STEADY: "비슷함",
  NO_COMPARISON: "비교 없음",
} as const satisfies Record<ReportTrend, string>;

/** 관심 변화의 이름 */
export const shiftLabels = {
  ENTERED: "새로 생긴 관심",
  LEFT: "사라진 관심",
  NONE: "변화 없음",
} as const satisfies Record<InterestShift, string>;

/** 숫자 표의 열 이름 */
export const countColumns = {
  flow: "흐름",
  topic: "주제",
  current: "이번 기간",
  previous: "직전 기간",
  trend: "판정",
  start: "기간 시작",
  end: "기간 끝",
  shift: "변화",
} as const;

/** 직전 값이 없는 칸 */
export const NO_PREVIOUS_VALUE = "—";

/** 관심 값이 없는 칸 */
export const NO_LEVEL = "신호 없음";

/** 관심 값 — 소수 둘째 자리 */
export function levelText(level: number): string {
  return level.toFixed(2);
}

/** 기간 안에 추적을 더한 흐름의 표시 */
export const TRACKED_IN_PERIOD = "이번 기간에 추적 시작";

/** 인용 목록의 이름 */
export const CITATIONS_TITLE = "이 섹션이 다룬 흐름 · 사건";

/** 대표 기사를 찾지 못한 사건 — 링크 없음 */
export const EVENT_UNTITLED = "대표 기사를 찾지 못한 사건";

/** 인용한 흐름 · 사건이 사라져 숨긴 섹션 */
export function hiddenSectionsText(count: number): string {
  return `인용한 흐름 · 사건이 사라져 섹션 ${count}개를 숨겼습니다.`;
}

/** 갈래 섹션이 모두 숨어 글이 하나도 남지 않은 리포트 */
export const ALL_HIDDEN =
  "이 리포트가 인용한 흐름 · 사건이 모두 사라져 글을 보이지 않습니다.";

/** 비교한 직전 리포트로 가는 링크 */
export function previousLink(
  kind: ReportKind,
  firstDay: string,
  lastDay: string,
): string {
  return `비교한 직전 ${kindLabels[kind]} 리포트 · ${periodText(firstDay, lastDay)}`;
}

/** 비교한 직전 리포트가 없을 때 */
export const NO_PREVIOUS =
  "비교한 직전 리포트가 없습니다 — 첫 리포트이거나 직전 기간과 이어지지 않아 직전 기간 값이 비어 있습니다.";

/** 사용량 줄 — 금액으로 바꾸지 않는다 */
export function usageText(
  model: string,
  inputTokens: number | null,
  outputTokens: number | null,
): string {
  if (inputTokens === null || outputTokens === null) {
    return `모델 ${model}`;
  }
  return `모델 ${model} · 입력 ${inputTokens.toLocaleString("ko-KR")} · 출력 ${outputTokens.toLocaleString("ko-KR")} 토큰`;
}

/** 목록이 비었을 때 — 어떻게 생기는지 */
export const NO_REPORTS =
  "아직 받은 리포트가 없습니다. 관심 설정에서 주간 · 월간 변화 리포트를 켜면 정한 일정에 만들어집니다.";

/** 관심 설정 리포트 절에서 목록으로 가는 링크 */
export const INTERESTS_LINK = "받은 변화 리포트 보기";

/** 목록으로 돌아가는 링크 */
export const BACK_TO_LIST = "변화 리포트 목록";

/** 없는 리포트 */
export const NOT_FOUND = "없는 리포트입니다.";

/** 목록을 읽지 못했을 때 */
export const LIST_LOAD_FAILED = "리포트 목록을 불러오지 못했습니다.";

/** 한 편을 읽지 못했을 때 */
export const LOAD_FAILED = "리포트를 불러오지 못했습니다.";
