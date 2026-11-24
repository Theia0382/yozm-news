import type { components } from "@/shared/api/schema";

// 관심 설정 화면의 문구 — 판별자마다 하나. 값이 늘면 타입 오류로 드러난다.

export type ModelReason =
  components["schemas"]["InterestModelResponse"]["reason"];

export type Weekday =
  components["schemas"]["InterestSettingsResponse"]["weeklyReportDay"];

export type CandidateVolume =
  components["schemas"]["InterestVolumeResponse"]["volume"];

export type SummaryLength =
  components["schemas"]["InterestVolumeResponse"]["summaryLength"];

export type ReasoningEffort =
  components["schemas"]["InterestModelResponse"]["effort"];

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

/** 최대 후보 수 단계의 이름 — 값(건수)은 서버 응답에서 붙인다 */
export const candidateVolumeNames: Record<CandidateVolume, string> = {
  FEW: "적게",
  NORMAL: "보통",
  MANY: "많이",
  CUSTOM: "직접 입력",
};

/** 다이제스트 분량 절의 설명 */
export const CANDIDATE_VOLUME_HELP_TEXT =
  "후보는 다이제스트에 넣을 사건(한 번 일어난 일을 다룬 기사 묶음)입니다. 사건마다 기사 몇 건을 LLM 공급자에게 보내므로 많을수록 입력 토큰(비용)이 늘고, 모델의 입력 한도에 걸리면 그보다 적게 보냅니다.";

export const BIG_SLOTS_HELP_TEXT =
  "구독하지 않은 주제라도 그 기간에 크게 보도된 소식(큰 화제)을 몇 개까지 넣을지 정합니다. 큰 화제 사건은 최대 후보 수의 절반을 넘지 않습니다.";

export const BIG_SLOTS_OFF_TEXT =
  "0이면 큰 화제를 넣지 않아, 구독한 주제 밖의 소식은 받지 않습니다.";

/** 요약 길이 단계의 이름 */
export const summaryLengthNames: Record<SummaryLength, string> = {
  SHORT: "짧게",
  NORMAL: "보통",
  DETAILED: "자세히",
};

export const SUMMARY_LENGTH_HELP_TEXT =
  "다이제스트의 개요와 주요 뉴스 요약을 얼마나 길게 쓸지 정합니다. 길수록 출력 토큰(비용)이 늘고 읽는 시간도 늡니다.";

/** 추론 강도의 이름 — 낮음부터 */
export const reasoningEffortNames: Record<ReasoningEffort, string> = {
  LOW: "낮음",
  MEDIUM: "보통",
  HIGH: "높음",
};

export const REASONING_EFFORT_HELP_TEXT =
  "다이제스트와 변화 리포트를 만들 때 모델이 얼마나 깊이 생각할지 정합니다. 높을수록 여러 기사를 종합해 쓰는 품질이 나아질 수 있지만 출력 토큰(비용)과 만드는 시간이 늡니다.";
