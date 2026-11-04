import type { Schemas } from "@/features/admin/adminClient";

// 서버 열거값의 화면 문구. 생성 타입의 값이 늘면 satisfies 가 타입 오류를 낸다(빠뜨린 값이 화면에서 조용히 사라지지 않게).

type Refusal = NonNullable<Schemas["FeedEnableResponse"]["refusal"]>;
type Criterion = Schemas["CriterionShortfall"]["criterion"];
type FailureReason = Schemas["FetchFailureView"]["reason"];
type Permanence = Schemas["FetchFailureView"]["permanence"];
type SkipReason = Schemas["SkippedItemView"]["reason"];
type DocumentProblemKind = Schemas["DocumentProblemView"]["kind"];
type ProfileState = Schemas["SourceResponse"]["profileState"];
type FeedStatus = Schemas["FeedCollectionResponse"]["status"];
type StrandedReason = Schemas["StrandedPublicationResponse"]["reason"];
type ResubmissionOutcome = Schemas["ResubmissionItemResponse"]["result"];
type DiscardOutcome = Schemas["DiscardItemResponse"]["result"];
type StoredRange = Schemas["ProbabilityBucket"]["stored"];
type FeedRemovalRefusal = NonNullable<
  Schemas["FeedRemovalResponse"]["refusal"]
>;
type TopicStatus = Schemas["EvaluationRowResponse"]["topicStatus"];
type ThresholdWarning = Schemas["ThresholdChangeResponse"]["warnings"][number];
type ThresholdCondition = Schemas["ThresholdKindStatus"]["conditions"][number];
type MultiTagKind = Schemas["ThresholdPreviewRequest"]["kind"];

export const refusalLabels = {
  PROFILE_UNAVAILABLE:
    "프로필을 얻지 못했습니다 — 프리셋이 사라졌거나 운영자 원문이 검증에 걸립니다",
  TERMS_NOT_REVIEWED:
    "이용조건 검토일(termsReviewedAt)이 없습니다 — 원문에 검토일을 적어야 켤 수 있습니다",
  FEED_NOT_IN_PROFILE: "프로필에 이 피드가 없습니다",
} as const satisfies Record<Refusal, string>;

export const criterionLabels = {
  ARTICLES: "기사 수",
  SKIPPED_ITEMS: "건너뛴 항목 비율",
  DATED_ARTICLES: "발행 시각을 해석한 비율",
  REPLACED_CHARACTERS: "읽지 못해 바꾼 문자 비율",
} as const satisfies Record<Criterion, string>;

/** 기준이 비율인가 — 기사 수만 개수다 */
export const criterionIsRatio = {
  ARTICLES: false,
  SKIPPED_ITEMS: true,
  DATED_ARTICLES: true,
  REPLACED_CHARACTERS: true,
} as const satisfies Record<Criterion, boolean>;

export const failureReasonLabels = {
  TIMEOUT: "시간 초과",
  CONNECTION_FAILED: "연결 실패",
  NAME_NOT_RESOLVED: "호스트 이름 해석 실패",
  SERVER_ERROR: "서버 오류",
  THROTTLED: "요청 제한",
  ACCESS_DENIED: "접근 거부",
  NOT_FOUND: "피드 없음",
  CLIENT_ERROR: "요청 오류",
  UNEXPECTED_STATUS: "예상하지 못한 응답",
  TOO_MANY_REDIRECTS: "리다이렉트 초과",
  INVALID_REDIRECT: "잘못된 리다이렉트",
  UNSUPPORTED_URL: "요청할 수 없는 주소",
  BLOCKED_ADDRESS: "내부 주소 차단",
  TLS_FAILURE: "TLS 실패",
  TOO_LARGE: "크기 초과",
  UNREADABLE: "피드 문서를 읽지 못함",
} as const satisfies Record<FailureReason, string>;

export const permanenceLabels = {
  TEMPORARY: "일시적",
  PERMANENT: "영구적",
} as const satisfies Record<Permanence, string>;

export const skipReasonLabels = {
  MISSING_LINK: "링크 없음",
  INVALID_LINK: "웹 주소가 아닌 링크",
  MISSING_TITLE: "제목 없음",
} as const satisfies Record<SkipReason, string>;

export const documentProblemLabels = {
  UNSUPPORTED_ENCODING: "지원하지 않는 문자 인코딩",
  MALFORMED_XML: "XML 형식 오류",
  DTD_DECLARATION: "DTD 선언 · 선언되지 않은 엔티티",
  NOT_A_FEED: "RSS · Atom 피드가 아님",
} as const satisfies Record<DocumentProblemKind, string>;

export const profileStateLabels = {
  AVAILABLE: "프로필 정상",
  PRESET_MISSING: "참조하던 프리셋이 사라졌습니다",
  INVALID: "운영자 원문이 검증에 걸립니다",
} as const satisfies Record<ProfileState, string>;

export const feedStatusLabels = {
  ACTIVE: "정상",
  DEGRADED: "계속 실패",
  STALE: "오래 새 기사 없음",
} as const satisfies Record<FeedStatus, string>;

export const strandedReasonLabels = {
  EXHAUSTED: "처리 시도 상한에 이름",
  UNREADABLE: "이벤트를 읽을 수 없음 — 폐기만 가능",
} as const satisfies Record<StrandedReason, string>;

export const resubmissionOutcomeLabels = {
  RESUBMITTED: "다시 처리함",
  NOT_EXHAUSTED: "상한에 이른 발행이 아님(상한 미만 · 처리 중 · 그사이 바뀜)",
  UNREADABLE: "이벤트를 읽을 수 없어 다시 처리하지 못함 — 폐기만 가능",
  NOT_FOUND: "이미 없음(완료됐거나 폐기됨)",
} as const satisfies Record<ResubmissionOutcome, string>;

export const discardOutcomeLabels = {
  DISCARDED: "폐기함",
  NOT_STRANDED: "남겨진 발행이 아니라 지우지 않음",
  CHANGED: "그사이 바뀌어 지우지 않음",
  NOT_FOUND: "이미 없음(완료됐거나 폐기됨)",
} as const satisfies Record<DiscardOutcome, string>;

export const feedRemovalRefusalLabels = {
  IN_PROFILE:
    "프로필에 있는 피드입니다 — 프로필에서 빼야 숨기거나 지울 수 있습니다",
  PROFILE_UNAVAILABLE:
    "프로필을 얻지 못해 프로필에 있는 피드인지 판정하지 못했습니다 — 프리셋이 사라졌거나 운영자 원문이 검증에 걸립니다",
} as const satisfies Record<FeedRemovalRefusal, string>;

/** 소스 이름 — 이름을 읽지 못했으면 그 사실을 보인다 */
export function sourceName(source: Schemas["SourceResponse"]): string {
  return source.name ?? "이름을 읽지 못한 소스";
}

/** 내부 주소로 가는 요청을 막았을 때의 안내 — 화면에서는 열 수 없다 */
export const BLOCKED_ADDRESS_HINT =
  "내부 주소는 서버 설정 YOZMNEWS_COLLECTION_ALLOWED_NETWORKS 로만 열 수 있습니다";

/** 확률 칸이 지금 저장 기준 이상인가 — 전부 저장되는 칸은 따로 적지 않는다 */
export const storedRangeLabels = {
  ALL: null,
  PART: "일부 저장",
  NONE: "저장하지 않음",
} as const satisfies Record<StoredRange, string | null>;

export const topicStatusLabels = {
  TAGGED: "태그 기준을 넘은 주제 있음",
  LOW_CONFIDENCE: "low_confidence",
  UNCLASSIFIED: "미분류",
} as const satisfies Record<TopicStatus, string>;

export const tagKindLabels = {
  TOPIC: "주제",
  REGION: "지역",
} as const satisfies Record<MultiTagKind, string>;

/** 기준 변경의 경고 — 변경을 막지 않는 알림이다(그 변경이 어긋남을 만들 때만 나온다) */
export const thresholdWarningLabels = {
  STORE_THRESHOLD_LOWERED:
    "저장 기준을 낮췄다 — 지난 평가에는 낮춘 구간의 확률이 없어, 그 구간 태그는 앞으로의 평가에만 붙는다",
  TAG_BELOW_PAST_STORE:
    "태그 기준을 이 버전에서 써 온 저장 기준보다 낮췄다 — 그 저장 기준으로 평가된 기사에는 그 구간 태그가 붙지 않는다",
  TAG_BELOW_STORE:
    "태그 기준이 저장 기준보다 낮아졌다 — 그 구간 확률은 저장되지 않아 붙지 않는다",
} as const satisfies Record<ThresholdWarning, string>;

/** 지금 값의 어긋난 상태 — 남아 있는 동안 나온다 */
export const thresholdConditionLabels = {
  TAG_BELOW_STORE:
    "태그 기준이 저장 기준보다 낮다 — 그 구간 확률은 저장되지 않아 붙지 않는다",
  TAG_BELOW_PAST_STORE:
    "태그 기준이 이 버전에서 써 온 저장 기준보다 낮다 — 그 저장 기준으로 평가된 기사에는 그 구간 태그가 없다",
  STORE_THRESHOLD_CHANGED:
    "이 버전의 저장 기준이 바뀐 적이 있다 — 평가마다 다른 저장 기준으로 저장됐다",
} as const satisfies Record<ThresholdCondition, string>;
