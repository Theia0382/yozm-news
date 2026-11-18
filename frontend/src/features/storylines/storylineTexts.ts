// 독자 화면의 스토리라인 문구 — 목록 · 상세 · 구독이 같은 문구를 쓰게 한 곳에 둔다(docs/specs/reader.md · overview §8).

import type { StorylineWindow } from "@/features/storylines/storylineQueries";
import { formatDate } from "@/shared/lib/dateTime";

/** 스토리라인 목록의 주소 */
export const STORYLINES_PATH = "/storylines";

/** 스토리라인 하나의 주소 */
export function storylinePath(id: number): string {
  return `${STORYLINES_PATH}/${id}`;
}

/** 화면 이름 */
export const STORYLINES_TITLE = "스토리라인";

/** 목록 머리의 설명 */
export const STORYLINES_INTRO =
  "여러 날에 걸쳐 이어지는 사건들의 흐름입니다. 모든 사용자가 같은 스토리라인을 봅니다.";

/** 큰 판정을 지난 스토리라인의 표시 */
export const BIG_STORYLINE = "큰 스토리라인";

/** 목록 기간의 이름 */
export const windowLabels = {
  DAY: "최근 24시간",
  WEEK: "최근 7일",
  MONTH: "최근 30일",
} as const satisfies Record<StorylineWindow, string>;

/** 목록 기간을 고르는 순서 */
export const WINDOWS: readonly StorylineWindow[] = ["DAY", "WEEK", "MONTH"];

/** 큰 표시의 설명 — 큰 여부는 고른 기간마다 다르다 */
export function bigInWindow(window: StorylineWindow): string {
  return `${windowLabels[window]} 동안 크게 다뤄졌습니다`;
}

/** 대표 기사를 읽지 못한 스토리라인 */
export const UNTITLED = "제목을 아직 정하지 못한 스토리라인";

/** 대표 기사를 읽지 못한 사건 */
export const EVENT_UNTITLED = "대표 기사를 아직 읽지 못한 사건";

/** 하위 스토리라인에서 물려받은 사건의 표시 */
export const INHERITED = "갈래";

/** 받은 소식 — 그 사용자가 다이제스트에서 받은 날짜 */
export function receivedOn(date: string): string {
  return `받은 소식 · ${formatDate(date)}`;
}

/** 관계 머리 */
export const relationHeadings = {
  parent: "상위 스토리라인",
  children: "하위 스토리라인",
  preceding: "앞 흐름",
  following: "이어진 흐름",
} as const;

/** 기간 안 기사가 있는 스토리라인이 없다 */
export const EMPTY_WINDOW = "이 기간에 기사가 있는 스토리라인이 없습니다.";

/** 없는 스토리라인 */
export const NOT_FOUND = "없는 스토리라인입니다.";

/** 읽지 못했다 */
export const LOAD_FAILED = "스토리라인을 불러오지 못했습니다.";

/** 구독 버튼 — 구독하지 않은 스토리라인 */
export const SUBSCRIBE_LABEL = "구독";

/** 구독 버튼 — 구독 중인 스토리라인(누르면 해제) */
export const SUBSCRIBED_LABEL = "구독 중";

/**
 * 구독의 효과 한 줄 — 구독 버튼의 설명. 구독은 그 흐름의 기사를 다이제스트에 따로 넣지 않고 주제별 관심도에만 한동안 든다(docs/specs/digest.md
 * 관심도 · ADR-0214)
 */
export const SUBSCRIPTION_EFFECT_SHORT =
  "구독하면 이 흐름의 주제를 다이제스트 순위에 한동안 반영합니다";

/** 구독 수가 상한이다 */
export function limitReached(limit: number): string {
  return `구독은 ${limit}개까지입니다. 내 구독에서 정리해 주세요.`;
}

/** 구독 · 해제 요청이 실패했다 */
export const SUBSCRIPTION_CHANGE_FAILED = "구독을 바꾸지 못했습니다.";
