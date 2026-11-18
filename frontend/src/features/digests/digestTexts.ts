// 독자 화면의 다이제스트 문구 — 홈 · 날짜 화면 · 목록과 알림 화면이 같은 문구를 쓰게 한 곳에 둔다(docs/specs/reader.md reader-R9 ~ R11).

import type {
  DigestDayState,
  DigestFailure,
  DigestFailureCause,
  SelectionReason,
} from "@/features/digests/digestQueries";
import { formatDate, formatDateTime } from "@/shared/lib/dateTime";

/** 홈 — 오늘 다이제스트 */
export const DIGEST_HOME_PATH = "/";

/** 지난 다이제스트 목록 */
export const DIGESTS_PATH = "/digests";

/** 날짜 하나의 다이제스트 */
export function digestPath(date: string): string {
  return `${DIGESTS_PATH}/${date}`;
}

/** 홈 화면 이름 */
export const DIGEST_HOME_TITLE = "오늘 다이제스트";

/** 목록 화면 이름 */
export const DIGEST_DATES_TITLE = "지난 다이제스트";

/** 날짜 하나의 화면 이름 */
export function digestDayTitle(date: string): string {
  return `${formatDate(date)} 다이제스트`;
}

/** 선정 이유의 화면 이름 — 구독 주제는 표시하지 않고 범례로 알린다(ADR-0241) */
export const selectionReasonLabels = {
  SUBSCRIBED: "구독 주제",
  INTEREST: "관심 반영",
  MAJOR_STORYLINE: "큰 화제",
} as const satisfies Record<SelectionReason, string>;

/** 선정 이유의 범례 */
export const SELECTION_REASON_LEGEND =
  "‘관심 반영’은 구독한 주제 가운데 좋아요 · 구독 변경으로 관심이 높아진 주제라서 앞 순위에 든 기사, ‘큰 화제’는 구독하지 않은 주제라도 이 기간에 크게 보도된 소식이라 들어온 기사입니다. 표시가 없는 기사는 구독한 주제에서 왔습니다.";

/** 섹션 머리 */
export const sectionHeadings = {
  overview: "요약",
  topStories: "주요 뉴스",
  moreHeadlines: "추가 헤드라인",
} as const;

/** 실패의 갈래마다 할 일 — 알림 화면도 이 문구를 쓴다 */
export const digestFailureCauseTexts: Record<DigestFailureCause, string> = {
  KEY: "LLM 키를 쓸 수 없었습니다. 키를 확인하거나 다시 등록하세요.",
  PROVIDER_ACCOUNT:
    "공급자 계정의 권한이나 잔액 때문에 만들지 못했습니다. 공급자 계정을 확인하세요.",
  TEMPORARY:
    "공급자가 잠시 답하지 못했거나 생성이 끝나지 않았습니다. 잠시 뒤 다시 만들어 보세요.",
  OUTPUT:
    "모델의 출력을 다이제스트로 쓰지 못했습니다. 다시 만들거나 다른 모델을 골라 보세요.",
};

/** 실패 코드마다 무슨 일이 있었는가(ADR-0240) */
export const digestFailureTexts: Record<DigestFailure, string> = {
  UNREADABLE_OUTPUT:
    "모델의 답을 다이제스트 형식으로 읽지 못했습니다. 한 번 더 물었지만 마찬가지였습니다.",
  NO_CITATION: "모델의 답에 후보 기사를 인용한 항목이 없었습니다.",
  KEY_MISSING: "쓸 모델의 공급자에 등록한 LLM 키가 없었습니다.",
  KEY_INVALID: "공급자가 등록한 LLM 키를 받아 주지 않았습니다.",
  PERMISSION_DENIED: "키에 이 모델을 쓸 권한이 없었습니다.",
  QUOTA_EXHAUSTED: "공급자 계정의 잔액이나 지출 한도가 소진됐습니다.",
  PROVIDER_UNAVAILABLE: "공급자가 여러 번 다시 보내도 답하지 못했습니다.",
  PROVIDER_UNEXPECTED: "공급자가 예상하지 못한 답을 했습니다.",
  OUTPUT_TRUNCATED: "모델의 답이 출력 한도에 닿아 잘렸습니다.",
  OUTPUT_REFUSED: "모델이 다이제스트 작성을 거절했습니다.",
  NO_USABLE_MODEL:
    "만들 때 쓸 모델이 없었습니다 — 유효한 키가 있는 공급자의 모델이 없었습니다.",
  INTERRUPTED: "정한 시간 안에 생성이 끝나지 않았습니다.",
};

/** 실패 상자의 머리 */
export const FAILED_HEADING = "다이제스트를 만들지 못했습니다";

/** 키 화면으로 가는 링크 */
export const KEYS_LINK = "LLM 키 화면에서 확인하기";

/** 보이는 다이제스트가 없고 만드는 중 */
export const IN_PROGRESS_TEXT =
  "다이제스트를 만드는 중입니다. 다 만들면 이 화면에 나타납니다.";

/** 보이는 다이제스트가 있고 다시 만드는 중 */
export const REGENERATING_TEXT =
  "다시 만드는 중입니다. 다 만들 때까지 지금 다이제스트가 보입니다.";

/** 보이는 다이제스트가 없고 후보가 없었다 */
export const EMPTY_TEXT = "이 기간에는 다이제스트에 넣을 기사가 없었습니다.";

/** 보이는 다이제스트가 있고 다시 만들기가 실패했다 */
export const RETRY_FAILED_TEXT =
  "다시 만들지 못해 앞서 만든 다이제스트를 보여 줍니다.";

/** 보이는 다이제스트가 있고 다시 만든 기간에 후보가 없었다 */
export const RETRY_EMPTY_TEXT =
  "다시 만든 기간에 넣을 기사가 없어 앞서 만든 다이제스트를 보여 줍니다.";

/** 인용한 기사가 모두 지워진 다이제스트(섹션 없음) */
export const NO_CONTENT_TEXT =
  "이 다이제스트가 인용한 기사가 모두 지워져 보일 내용이 없습니다.";

/** 다이제스트가 하나도 없다 */
export const NO_DIGEST_YET = "아직 만든 다이제스트가 없습니다.";

/** 날짜 화면의 그 날짜에 다이제스트가 없다 */
export const DAY_NOT_FOUND = "그 날짜의 다이제스트가 없습니다.";

/** 읽지 못했다 */
export const LOAD_FAILED = "다이제스트를 불러오지 못했습니다.";

/** 날짜 목록의 상태 이름(ADR-0238) */
export const dayStateLabels = {
  CONTENT: "다이제스트",
  IN_PROGRESS: "만드는 중",
  EMPTY: "넣을 기사 없음",
  FAILED: "만들지 못함",
} as const satisfies Record<DigestDayState, string>;

/** 다이제스트의 기간 */
export function periodText(start: string, end: string): string {
  return `기간 ${formatDateTime(start)} ~ ${formatDateTime(end)}`;
}

const numbers = new Intl.NumberFormat("ko-KR");

/** 보이는 다이제스트의 모델 · 토큰(ADR-0239) */
export function shownUsageText(
  model: string,
  candidateCount: number,
  inputTokens: number,
  outputTokens: number,
): string {
  return `모델 ${model} · 후보 ${numbers.format(candidateCount)}건 · 입력 토큰 ${numbers.format(inputTokens)} · 출력 토큰 ${numbers.format(outputTokens)}`;
}

/** 그 날짜 모든 시도의 토큰 합(ADR-0239) — 다시 만든 · 실패한 시도를 포함한다 */
export function dayUsageText(
  attempts: number,
  inputTokens: number,
  outputTokens: number,
): string {
  return `이 날짜에 ${numbers.format(attempts)}번 만들었고, 다시 만들거나 실패한 것까지 모두 입력 토큰 ${numbers.format(inputTokens)} · 출력 토큰 ${numbers.format(outputTokens)}을 썼습니다.`;
}

/** 오늘 다이제스트 안내 — 생성 시각 전(ADR-0237) */
export function scheduledTodayText(time: string, zone: string | null): string {
  return `오늘 다이제스트는 ${time}${zone === null ? "" : ` (${zone})`}에 만듭니다.`;
}

/** 오늘 다이제스트 안내 — 생성 시각이 지났거나 시각을 모를 때 */
export const NOT_YET_TODAY = "오늘 다이제스트가 아직 없습니다.";

/** 쓸 모델이 없다 */
export const NO_USABLE_MODEL_TEXT =
  "쓸 모델이 없어 다이제스트를 만들지 못합니다. LLM 키와 관심 설정의 모델을 확인하세요.";

/** 수동 생성 버튼 */
export const REQUEST_REGENERATE = "다시 만들기";
export const REQUEST_NEW = "지금 만들기";

/** 수동 생성 확인 창의 머리 */
export function requestTitle(targetDate: string, regenerates: boolean): string {
  return `${formatDate(targetDate)} 다이제스트를 ${regenerates ? "다시 만들까요?" : "지금 만들까요?"}`;
}

/** 수동 생성은 사용자 비용이다 */
export const REQUEST_COST_TEXT =
  "등록한 LLM 키로 공급자에게 요청합니다. 요청 비용은 내 공급자 계정에 청구됩니다.";

/** 오늘 남은 횟수 */
export function remainingText(remaining: number): string {
  return `오늘 ${remaining}번 더 만들 수 있습니다.`;
}

/** 다시 만들기 — 같은 기간 */
export const REGENERATE_TEXT = "같은 기간으로 다시 만듭니다.";

/** 다시 만들기 — 대상 날짜에 보이는 다이제스트가 있다 */
export const REGENERATE_KEEPS_TEXT =
  "다 만들 때까지 지금 다이제스트가 보이고, 만들지 못하면 지금 다이제스트가 그대로 남습니다.";

/** 새로 만들기 — 생성 시각 전이면 그날 일정 생성이 빠진다 */
export function skipsScheduleText(time: string): string {
  return `지금 만들면 오늘 ${time} 일정 생성은 하지 않습니다.`;
}

/** 오늘 횟수를 다 썼다 */
export function dailyLimitText(resetsAt: string): string {
  return `오늘은 더 만들 수 없습니다. ${formatDateTime(resetsAt)}부터 다시 만들 수 있습니다.`;
}

/** 수동 생성 결과 — 받음은 만드는 중 표시가 대신한다 */
export const IN_PROGRESS_REFUSED = "이미 만드는 중인 다이제스트가 있습니다.";
export const REQUEST_FAILED = "요청하지 못했습니다. 다시 시도해 주세요.";
