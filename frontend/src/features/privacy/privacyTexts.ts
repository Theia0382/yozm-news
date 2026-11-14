// 고지 문구의 정본 — 고지 페이지와 키 화면이 함께 읽는다(docs/security.md "개인정보").

/** 이 인스턴스가 저장하는 것 */
export const COLLECTED_ITEMS: readonly string[] = [
  "이메일, 표시 이름, 비밀번호 해시",
  "관심 설정",
  "관심 신호 이력(좋아요 · 싫어요, 주제와 스토리라인의 구독 변경)",
  "다이제스트와 변화 리포트",
  "암호화한 LLM 키",
];

/** 다이제스트를 만들 때 고른 공급자로 가는 것 */
export const DIGEST_TRANSFER_ITEMS: readonly string[] = [
  "구독 주제, 지역 선호, 자유 텍스트 관심사, 원하는 분량",
  "후보 기사의 정보",
];

/** 변화 리포트를 만들 때 고른 공급자로 가는 것 */
export const REPORT_TRANSFER_ITEMS: readonly string[] = [
  "그 기간에 구독한 스토리라인의 변화 · 보도량 · 관심도 통계",
  "직전 리포트의 요약",
];

/** 고지 페이지의 주소 */
export const PRIVACY_PATH = "/privacy";
