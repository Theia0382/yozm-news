// 독자 화면의 스토리라인 문구 — 목록 · 상세 · 구독이 같은 문구를 쓰게 한 곳에 둔다(docs/specs/reader.md · overview §8).

import type { StorylineWindow } from "@/features/storylines/storylineQueries";
import type { SubscriptionAction } from "@/features/storylines/subscriptionQueries";
import { formatDate, formatDateTime } from "@/shared/lib/dateTime";

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
 * 구독의 효과 한 줄 — 구독 버튼의 설명. 구독은 그 흐름(포함 나무의 꼭대기)의 관심에 들어 그 흐름 사건의 순위를 올리고, 구독한 흐름의 사건은
 * 구독하지 않은 주제여도 흐름마다 한 사건까지, 구독 사건 자리의 몫 상한 안에서 다이제스트에 들 수 있다 — 사건 제외 · 큰 슬롯 · 입력 토큰
 * 예산으로 빠질 수도 있다(docs/specs/digest.md 관심도 · 후보 선별 digest-R52)
 */
export const SUBSCRIPTION_EFFECT_SHORT =
  "구독하면 이 흐름의 소식이 다이제스트 순위에서 앞서고, 구독하지 않은 주제여도 들어올 수 있습니다";

/** 구독 수가 상한이다 */
export function limitReached(limit: number): string {
  return `구독은 ${limit}개까지입니다. 내 구독에서 정리해 주세요.`;
}

/** 구독 · 해제 요청이 실패했다 */
export const SUBSCRIPTION_CHANGE_FAILED = "구독을 바꾸지 못했습니다.";

/** 내 구독의 주소 — 스토리라인 화면 묶음 안에 있고 키가 있어야 간다(ADR-0242) */
export const SUBSCRIPTIONS_PATH = `${STORYLINES_PATH}/subscriptions`;

/** 내 구독 화면 이름 */
export const SUBSCRIPTIONS_TITLE = "내 구독";

/** 구독 해제 버튼 */
export const UNSUBSCRIBE_LABEL = "구독 해제";

/**
 * 구독의 효과 — 내 구독 머리의 설명. 버튼 설명(SUBSCRIPTION_EFFECT_SHORT)과 같은 사실이다: 구독은 그 흐름의 관심에 구독 · 해제한 때 한 번
 * 든 뒤 차츰 줄어들고, 구독한 흐름의 사건은 구독하지 않은 주제여도 흐름마다 한 사건까지, 구독 사건 자리의 몫 상한 안에서 다이제스트에 들 수
 * 있다(docs/specs/digest.md 관심도 · 후보 선별 digest-R52)
 */
export const SUBSCRIPTION_EFFECT =
  "구독하면 그 흐름을 관심 있게 본 것으로 다이제스트 순위에 한동안 반영하고, 구독하지 않은 주제여도 그 흐름의 소식을 받을 수 있습니다. 따라가는 흐름은 여기서 다시 찾아볼 수 있습니다.";

/** 지금 구독 수와 상한 */
export function subscriptionCount(count: number, limit: number): string {
  return `구독 ${count} / ${limit}`;
}

/** 내 구독의 순서 */
export type SubscriptionOrder = "LATEST_ARTICLE" | "SUBSCRIBED";

/** 내 구독 순서의 기본 — 최근 기사가 있는 흐름이 위 */
export const DEFAULT_ORDER: SubscriptionOrder = "LATEST_ARTICLE";

/** 내 구독 순서의 이름 */
export const orderLabels = {
  LATEST_ARTICLE: "최근 기사 순",
  SUBSCRIBED: "구독한 순",
} as const satisfies Record<SubscriptionOrder, string>;

/** 내 구독 순서를 고르는 목록의 차례 */
export const ORDERS: readonly SubscriptionOrder[] = [
  "LATEST_ARTICLE",
  "SUBSCRIBED",
];

/** 내 구독의 묶음 머리 — 끝나지 않은 것(진행 · 잠잠)과 끝난 것 */
export const groupHeadings = {
  following: "이어지는 스토리라인",
  ended: "끝난 스토리라인",
} as const;

/** 끝난 묶음의 설명 — 끝난 구독도 남고 뒤 기사가 붙으면 다시 이어진다(ADR-0189) */
export const ENDED_NOTE =
  "한동안 새 기사가 없는 흐름입니다. 다시 기사가 붙으면 위로 올라갑니다. 더 따라가지 않으려면 구독을 해제하세요.";

/** 구독한 스토리라인이 없다 */
export const NO_SUBSCRIPTIONS = "구독한 스토리라인이 없습니다.";

/** 그새 지워진 스토리라인 — 다시 읽으면 구독에서 빠진다 */
export const REMOVED_STORYLINE = "사라진 스토리라인";

/** 마지막 기사 시각 · 구독 시각의 한 줄 */
export function subscriptionTimes(
  lastArticleAt: string,
  subscribedAt: string,
): string {
  return `마지막 기사 ${formatDateTime(lastArticleAt)} · 구독 ${formatDateTime(subscribedAt)}`;
}

/** 내 구독을 읽지 못했다 */
export const SUBSCRIPTIONS_LOAD_FAILED = "내 구독을 불러오지 못했습니다.";

/** 구독 기록 절 이름 — 사용자가 직접 한 구독 · 해제(ADR-0243) */
export const HISTORY_TITLE = "구독 기록";

/** 구독 기록 행의 이름 */
export const historyActionLabels = {
  ADD: SUBSCRIBE_LABEL,
  REMOVE: UNSUBSCRIBE_LABEL,
} as const satisfies Record<SubscriptionAction, string>;

/** 구독 기록이 없다 */
export const NO_HISTORY = "구독 기록이 없습니다.";

/** 구독 기록을 읽지 못했다 */
export const HISTORY_LOAD_FAILED = "구독 기록을 불러오지 못했습니다.";
