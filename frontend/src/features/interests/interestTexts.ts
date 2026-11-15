import type { components } from "@/shared/api/schema";

// 관심 설정 화면의 문구 — 판별자마다 하나. 값이 늘면 타입 오류로 드러난다.

export type ModelReason =
  components["schemas"]["InterestModelResponse"]["reason"];

export type Weekday =
  components["schemas"]["InterestSettingsResponse"]["weeklyReportDay"];

/** 관심 설정 화면의 주소 */
export const INTERESTS_PATH = "/interests";

/** 고른 모델을 쓰지 못한 까닭 — 쓸 모델을 함께 보인다 */
export const modelReasonTexts: Record<
  Exclude<ModelReason, "CHOSEN">,
  string
> = {
  NOT_CHOSEN: "아직 모델을 고르지 않았습니다.",
  NOT_ALLOWED: "고른 모델이 이 인스턴스에서 더는 쓰이지 않습니다.",
  NO_KEY: "고른 모델의 공급자에 등록한 키가 없습니다.",
  KEY_INVALID: "고른 모델의 공급자 키가 무효입니다.",
};

/** 요일의 이름 — 월요일부터 */
export const weekdayNames: Record<Weekday, string> = {
  MONDAY: "월요일",
  TUESDAY: "화요일",
  WEDNESDAY: "수요일",
  THURSDAY: "목요일",
  FRIDAY: "금요일",
  SATURDAY: "토요일",
  SUNDAY: "일요일",
};

/** 구독한 주제가 없을 때의 안내 — 홈과 관심 설정이 함께 쓴다 */
export const NO_TOPIC_TEXT =
  "구독한 주제가 없어 다이제스트에 구독 기사가 들어가지 않습니다. 관심 있는 주제를 고르세요.";
