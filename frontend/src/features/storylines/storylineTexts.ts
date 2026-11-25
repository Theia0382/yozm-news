// 독자 화면의 사건 흐름 문구 — 탭 · 개인 흐름 화면 · 공용 타임라인 · 흐름 추적이 같은 문구를 쓰게 한 곳에 둔다(docs/specs/reader.md ·
// overview §8). 화면에서는 스토리라인을 "사건 흐름"(탭 · 화면 제목) · "흐름"(본문)이라 부른다 — 개발 이름은 스토리라인 그대로다.

import type { SubscriptionAction } from "@/features/storylines/subscriptionQueries";
import { formatDate, formatDateTime } from "@/shared/lib/dateTime";

const numbers = new Intl.NumberFormat("ko-KR");

/** 사건 흐름 탭(정리된 내 사용자 스토리라인 목록)의 주소 */
export const STORYLINES_PATH = "/storylines";

/** 공용 스토리라인 하나(사건 타임라인)의 주소 */
export function storylinePath(id: number): string {
  return `${STORYLINES_PATH}/${id}`;
}

/** 정리된 사용자 스토리라인 하나(개인 흐름 화면)의 주소 */
export function userStorylinePath(id: number): string {
  return `/user-storylines/${id}`;
}

/** 화면 이름 — 탭과 화면 제목 */
export const STORYLINES_TITLE = "사건 흐름";

/** 탭 머리의 설명 — 정리는 그 사용자의 LLM 키로 만든다 */
export const STORYLINES_INTRO =
  "좋아요를 거듭 눌렀거나 추적 중인 흐름을 내 LLM 키로 정리해 보입니다. 다이제스트를 받은 뒤 새 소식을 반영해 고칩니다.";

/** 정리된 사용자 스토리라인이 없다 */
export const NO_USER_STORYLINES =
  "아직 정리된 흐름이 없습니다. 다이제스트 항목에 좋아요를 누르거나 흐름을 추적하면, 다음 다이제스트를 받은 뒤 여기에 정리됩니다.";

/** 정리의 글 머리 */
export const summaryHeadings = {
  summary: "흐름 요약",
  recent: "최근 전개",
} as const;

/** 마지막으로 정리한 시각 한 줄 */
export function summarizedAt(updatedAt: string): string {
  return `정리 ${formatDateTime(updatedAt)}`;
}

/** 사용자 스토리라인을 이루는 갈래(추적 대상 스토리라인)가 둘 이상일 때의 수 */
export function partCount(count: number): string {
  return `갈래 ${numbers.format(count)}개`;
}

/** 개인 흐름 화면의 갈래 절 이름 */
export const PARTS_TITLE = "이 흐름의 갈래";

/** 아직 글이 없는 갈래 — 정리할 사건이 없었거나 마지막 정리 뒤에 이어졌다 */
export const PART_UNWRITTEN = "아직 정리되지 않은 갈래";

/** 갈래의 공용 타임라인으로 가는 링크 */
export const TIMELINE_LINK = "사건 타임라인 보기";

/** 개인 흐름 화면의 지난 다이제스트 항목 절 이름 */
export const ITEMS_TITLE = "이 흐름의 지난 다이제스트 항목";

/** 그 사용자 스토리라인에 묶인 지난 다이제스트 항목이 없다 */
export const NO_ITEMS = "아직 이 흐름에 묶인 다이제스트 항목이 없습니다.";

/** 제목이 없는 지난 다이제스트 항목 */
export const ITEM_UNTITLED = "제목 없는 항목";

/** 지난 다이제스트 항목의 종류 */
export const itemKindLabels = {
  TOP_STORY: "주요 뉴스",
  HEADLINE: "헤드라인",
  OVERVIEW: "개요",
} as const;

/** 지난 다이제스트 항목을 읽지 못했다 */
export const ITEMS_LOAD_FAILED = "지난 다이제스트 항목을 불러오지 못했습니다.";

/** 마지막 정리의 모델 · 토큰 — 다이제스트의 사용량 줄과 같은 꼴이다(금액으로 바꾸지 않는다) */
export function summaryUsageText(
  model: string,
  inputTokens: number,
  outputTokens: number,
): string {
  return `모델 ${model} · 입력 토큰 ${numbers.format(inputTokens)} · 출력 토큰 ${numbers.format(outputTokens)}`;
}

/** 이 사용자 스토리라인을 정리하느라 지금까지 쓴 토큰 합 — 다시 정리한 · 실패한 호출을 포함한다 */
export function summaryTotalUsageText(
  calls: number,
  inputTokens: number,
  outputTokens: number,
): string {
  return `이 흐름을 ${numbers.format(calls)}번 정리했고, 고쳐 쓰거나 실패한 것까지 모두 입력 토큰 ${numbers.format(inputTokens)} · 출력 토큰 ${numbers.format(outputTokens)}을 썼습니다.`;
}

/** 대표 기사를 읽지 못한 스토리라인 */
export const UNTITLED = "제목을 아직 정하지 못한 흐름";

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
  parent: "상위 흐름",
  children: "하위 흐름",
  preceding: "앞 흐름",
  following: "이어진 흐름",
} as const;

/** 없는 스토리라인 */
export const NOT_FOUND = "없는 흐름입니다.";

/** 읽지 못했다 */
export const LOAD_FAILED = "흐름을 불러오지 못했습니다.";

/** 흐름 추적 버튼 — 추적하지 않는 스토리라인 */
export const SUBSCRIBE_LABEL = "흐름 추적";

/** 흐름 추적 버튼 — 추적 중인 사용자 스토리라인(누르면 해제) */
export const SUBSCRIBED_LABEL = "추적 중";

/** 다이제스트 항목의 흐름 추적 버튼 이름 — 항목 제목을 담아 어느 항목의 버튼인지 알린다 */
export function trackingNameOf(title: string, label: string): string {
  return `${title} ${label}`;
}

/**
 * 흐름 추적의 효과 한 줄 — 흐름 추적 버튼의 설명. 추적은 그 사용자 스토리라인(누른 스토리라인의 포함 나무와 거기서 이어지는 뒤
 * 스토리라인)의 관심에 들어 그 사용자 스토리라인 사건의 순위를 올리고, 추적한 사용자 스토리라인의 사건은 구독하지 않은 주제여도 스토리라인마다 한 사건까지, 구독 사건 자리의 몫
 * 상한 안에서 다이제스트에 들 수 있다 — 사건 제외 · 큰 슬롯 · 입력 토큰 예산으로 빠질 수도 있다(docs/specs/digest.md 관심도 · 후보 선별
 * digest-R52)
 */
export const SUBSCRIPTION_EFFECT_SHORT =
  "추적하면 이 흐름과 여기서 이어지는 흐름의 소식이 다이제스트 순위에서 앞서고, 구독하지 않은 주제여도 들어올 수 있습니다";

/** 추적 중인 사용자 스토리라인 수가 상한이다 */
export function limitReached(limit: number): string {
  return `흐름 추적은 ${limit}개까지입니다. 추적 중인 흐름에서 정리해 주세요.`;
}

/** 추적 · 해제 요청이 실패했다 */
export const SUBSCRIPTION_CHANGE_FAILED = "흐름 추적을 바꾸지 못했습니다.";

/** 추적 상태를 읽지 못했다 — 버튼을 누를 수 없다 */
export const TRACKING_STATES_FAILED =
  "흐름 추적 상태를 불러오지 못해 지금은 추적을 바꿀 수 없습니다.";

/** 추적 중인 사용자 스토리라인의 주소 — 스토리라인 화면 묶음 안에 있고 키가 있어야 간다(ADR-0242) */
export const SUBSCRIPTIONS_PATH = `${STORYLINES_PATH}/subscriptions`;

/** 추적 중인 흐름 화면 이름 */
export const SUBSCRIPTIONS_TITLE = "추적 중인 흐름";

/** 추적 해제 버튼 — 그 줄의 사용자 스토리라인(헤드로 보낸다)의 추적만 끈다. 같은 앞 스토리라인에서 갈라진 다른 줄은 남는다 */
export const UNSUBSCRIBE_LABEL = "추적 해제";

/**
 * 구독의 효과 — 추적 중인 사용자 스토리라인 머리의 설명. 버튼 설명(SUBSCRIPTION_EFFECT_SHORT)과 같은 사실이다: 구독은 그 사용자 스토리라인의 관심에 구독 · 해제한 때 한 번
 * 든 뒤 차츰 줄어들고, 구독한 사용자 스토리라인의 사건은 구독하지 않은 주제여도 스토리라인마다 한 사건까지, 구독 사건 자리의 몫 상한 안에서 다이제스트에 들 수
 * 있다(docs/specs/digest.md 관심도 · 후보 선별 digest-R52)
 */
export const SUBSCRIPTION_EFFECT =
  "추적하면 그 흐름을 관심 있게 본 것으로 다이제스트 순위에 한동안 반영하고, 구독하지 않은 주제여도 그 흐름의 소식을 받을 수 있습니다. 그 흐름에서 이어지는 흐름도 함께 따라갑니다. 추적하는 흐름은 여기서 다시 찾아볼 수 있습니다.";

/** 지금 추적 중인 사용자 스토리라인 수와 상한 */
export function subscriptionCount(count: number, limit: number): string {
  return `추적 ${count} / ${limit}`;
}

/** 추적 중인 사용자 스토리라인의 순서 */
export type SubscriptionOrder = "LATEST_ARTICLE" | "SUBSCRIBED";

/** 추적 중인 사용자 스토리라인 순서의 기본 — 최근 기사가 있는 스토리라인이 위 */
export const DEFAULT_ORDER: SubscriptionOrder = "LATEST_ARTICLE";

/** 추적 중인 사용자 스토리라인 순서의 이름 */
export const orderLabels = {
  LATEST_ARTICLE: "최근 기사 순",
  SUBSCRIBED: "추적한 순",
} as const satisfies Record<SubscriptionOrder, string>;

/** 추적 중인 사용자 스토리라인 순서를 고르는 목록의 차례 */
export const ORDERS: readonly SubscriptionOrder[] = [
  "LATEST_ARTICLE",
  "SUBSCRIBED",
];

/** 추적 중인 사용자 스토리라인의 묶음 머리 — 끝나지 않은 것(진행 · 잠잠)과 끝난 것 */
export const groupHeadings = {
  following: "이어지는 흐름",
  ended: "끝난 흐름",
} as const;

/** 끝난 묶음의 설명 — 끝난 구독도 남고 뒤 기사가 붙으면 다시 이어진다(ADR-0189) */
export const ENDED_NOTE =
  "한동안 새 기사가 없는 흐름입니다. 다시 기사가 붙으면 위로 올라갑니다. 더 따라가지 않으려면 추적을 해제하세요.";

/** 추적 중인 사용자 스토리라인이 없다 */
export const NO_SUBSCRIPTIONS = "추적 중인 흐름이 없습니다.";

/** 추적 중인 사용자 스토리라인이 없을 때 추적하는 길 */
export const TRACK_HOW =
  "다이제스트 항목이나 사건 타임라인의 흐름 추적 버튼으로 추적할 수 있습니다.";

/** 추적 시각 한 줄 — 그새 지워진 스토리라인의 줄 */
export function trackedAt(subscribedAt: string): string {
  return `추적 ${formatDateTime(subscribedAt)}`;
}

/** 그새 지워진 스토리라인 — 다시 읽으면 구독에서 빠진다 */
export const REMOVED_STORYLINE = "사라진 흐름";

/** 마지막 기사 시각 · 구독 시각의 한 줄 */
export function subscriptionTimes(
  lastArticleAt: string,
  subscribedAt: string,
): string {
  return `마지막 기사 ${formatDateTime(lastArticleAt)} · 추적 ${formatDateTime(subscribedAt)}`;
}

/** 추적 중인 사용자 스토리라인을 읽지 못했다 */
export const SUBSCRIPTIONS_LOAD_FAILED =
  "추적 중인 흐름을 불러오지 못했습니다.";

/** 구독 기록 절 이름 — 사용자가 직접 한 구독 · 해제(ADR-0243) */
export const HISTORY_TITLE = "추적 기록";

/** 구독 기록 행의 이름 */
export const historyActionLabels = {
  ADD: SUBSCRIBE_LABEL,
  REMOVE: UNSUBSCRIBE_LABEL,
} as const satisfies Record<SubscriptionAction, string>;

/** 구독 기록이 없다 */
export const NO_HISTORY = "추적 기록이 없습니다.";

/** 구독 기록을 읽지 못했다 */
export const HISTORY_LOAD_FAILED = "추적 기록을 불러오지 못했습니다.";
