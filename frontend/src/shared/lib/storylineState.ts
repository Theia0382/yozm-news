import type { components } from "@/shared/api/schema";

/** 스토리라인 상태 — 명세의 상태 열거 */
export type StorylineState =
  components["schemas"]["StorylineCardResponse"]["state"];

/** 스토리라인 상태의 화면 표기 — 정본. 마지막 기사 뒤 지난 시간으로 조회할 때 정해진다. 운영자 화면과 독자 화면이 함께 쓴다 */
export const storylineStateLabels = {
  ACTIVE: "진행",
  QUIET: "잠잠",
  ENDED: "끝남",
} as const satisfies Record<StorylineState, string>;
