import type { components } from "@/shared/api/schema";

/** 피드 상태 — 명세의 피드 상태 열거 */
export type FeedStatus =
  components["schemas"]["FeedStatusEntryResponse"]["lastTo"];

/** 피드 상태의 화면 표기 — 정본. 운영자 화면과 알림 페이지가 함께 쓴다 */
export const feedStatusLabels = {
  ACTIVE: "정상",
  DEGRADED: "계속 실패",
  STALE: "오래 새 기사 없음",
} as const satisfies Record<FeedStatus, string>;
