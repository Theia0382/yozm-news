// 관심사 히스토리 화면의 문구 — 한 곳에 둔다(docs/specs/reader.md reader-R8).

import type {
  InterestPeriod,
  InterestSpan,
  InterestUnit,
} from "@/features/interestHistory/interestHistoryQueries";
import { formatDate } from "@/shared/lib/dateTime";

/** 관심사 히스토리의 주소 */
export const INTEREST_HISTORY_PATH = "/interest-history";

/** 화면 이름 */
export const INTEREST_HISTORY_TITLE = "관심사 히스토리";

/** 화면 머리의 설명 — 이 화면이 보이는 것 */
export const INTEREST_HISTORY_INTRO =
  "주제마다 관심이 기간에 따라 어떻게 달라졌는지 보여 줍니다. 단위(주 · 월)와 기간 수를 고를 수 있고, 마지막 기간은 지금 진행 중인 기간입니다.";

/** 관심이 바뀌는 까닭의 설명 — 제목 */
export const HOW_TITLE = "관심은 어떻게 바뀌나요";

/** 관심이 바뀌는 까닭의 설명 — 줄마다 하나 */
export const HOW_LINES: readonly string[] = [
  "받은 다이제스트의 항목에 좋아요를 누르면 그 항목 주제의 관심이 오르고, 싫어요를 누르면 내려갑니다.",
  "관심 설정에서 주제를 구독하면 오르고, 구독을 빼면 내려갑니다. 구독한 스토리라인은 그 사건들이 다룬 주제에 나눠 더해집니다.",
  "다이제스트에 나왔지만 좋아요 · 싫어요를 누르지 않은 항목이 많을수록 그 주제의 관심은 중립 쪽으로 돌아옵니다.",
  "오래된 반응 · 구독일수록 덜 셉니다.",
  "관심이 기준을 넘으면 그 기간에 새로 생긴 관심으로, 기준 아래로 내려가면 사라진 관심으로 표시합니다. 싫어요 쪽 관심은 붉은 칸으로 보입니다.",
];

/** 단위의 이름 */
export const unitLabels = {
  WEEK: "주",
  MONTH: "월",
} as const satisfies Record<InterestUnit, string>;

/** 보일 기간의 이름 */
export const spanLabels = {
  WEEKS_13: "최근 13주",
  WEEKS_26: "최근 26주",
  WEEKS_52: "최근 52주",
  MONTHS_12: "최근 12개월",
  MONTHS_24: "최근 24개월",
  MONTHS_36: "최근 36개월",
} as const satisfies Record<InterestSpan, string>;

/** 기간 열의 짧은 이름 — 주는 첫날(월/일), 월은 연.월. 날짜 문자열을 그대로 쪼갠다(시간대를 옮기지 않는다) */
export function periodLabel(
  period: InterestPeriod,
  unit: InterestUnit,
): string {
  const [year, month, day] = period.firstDay.split("-");
  return unit === "WEEK"
    ? `${Number(month)}/${Number(day)}`
    : `${year}.${month}`;
}

/** 기간의 전체 날짜 범위 */
export function periodRange(period: InterestPeriod): string {
  const range = `${formatDate(period.firstDay)} ~ ${formatDate(period.lastDay)}`;
  return period.inProgress ? `${range} (진행 중)` : range;
}

/** 관심 칸의 설명 — 값이 없으면 신호가 없는 기간이다 */
export function levelText(level: number | null): string {
  if (level === null) {
    return "신호 없음";
  }
  return `관심 ${level >= 0 ? "+" : ""}${level.toFixed(2)}`;
}

export const ENTERED = "새로 생긴 관심";

export const LEFT = "사라진 관심";

export const SUBSCRIBED = "구독 더함";

export const UNSUBSCRIBED = "구독 뺌";

export const IN_PROGRESS = "진행 중";

/** 변화 목록의 이름 */
export const CHANGES_TITLE = "기간마다 바뀐 것";

/** 신호가 없는 사용자 */
export const EMPTY_HISTORY =
  "아직 관심의 흐름을 보일 신호가 없습니다. 다이제스트를 받고 좋아요 · 싫어요를 누르거나 관심 설정에서 주제를 구독하면 쌓입니다.";

/** 고른 기간에 변화가 없다 */
export const NO_CHANGES =
  "이 기간에는 새로 생기거나 사라진 관심, 구독 변경이 없습니다.";

export const LOAD_FAILED =
  "관심사 히스토리를 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요.";

/** 드러나지 않은 주제를 펼치는 단추 */
export function moreTopics(count: number): string {
  return `그 밖의 주제 ${count}개 보기`;
}

export const FEWER_TOPICS = "그 밖의 주제 접기";
