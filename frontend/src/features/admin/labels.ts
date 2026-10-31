import type { Schemas } from "@/features/admin/adminClient";

// 서버 열거값의 화면 문구. 생성 타입의 값이 늘면 satisfies 가 타입 오류를 낸다(빠뜨린 값이 화면에서 조용히 사라지지 않게).

type Refusal = NonNullable<Schemas["FeedEnableResponse"]["refusal"]>;
type Criterion = Schemas["CriterionShortfall"]["criterion"];
type FailureReason = Schemas["FetchFailureView"]["reason"];
type Permanence = Schemas["FetchFailureView"]["permanence"];
type SkipReason = Schemas["SkippedItemView"]["reason"];
type DocumentProblemKind = Schemas["DocumentProblemView"]["kind"];
type ProfileState = Schemas["SourceResponse"]["profileState"];

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

/** 내부 주소로 가는 요청을 막았을 때의 안내 — 화면에서는 열 수 없다 */
export const BLOCKED_ADDRESS_HINT =
  "내부 주소는 서버 설정 YOZMNEWS_COLLECTION_ALLOWED_NETWORKS 로만 열 수 있습니다";
