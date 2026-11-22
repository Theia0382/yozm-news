// 고지 문구의 정본 — 고지 페이지와 키 화면이 함께 읽는다(docs/security.md "개인정보").

/** 이 인스턴스가 저장하는 것 */
export const COLLECTED_ITEMS: readonly string[] = [
  "이메일, 표시 이름, 비밀번호 해시",
  "관심 설정",
  "관심 신호 이력(좋아요 · 싫어요, 주제 구독과 흐름 추적의 변경)",
  "다이제스트와 변화 리포트",
  "암호화한 LLM 키",
  "요청 제한을 위한 IP 주소의 다이제스트 — 제한 창이 끝나면 지웁니다(창은 길어야 1시간)",
];

/** 다이제스트를 만들 때 쓸 모델의 공급자로 가는 것 */
export const DIGEST_TRANSFER_ITEMS: readonly string[] = [
  "구독 주제, 지역 선호, 자유 텍스트 관심사",
  "시간대 — 기사의 발행 시각을 그 시간대로 적습니다",
  "요약 길이",
  "다이제스트 항목(주요 뉴스 · 헤드라인)마다 사건으로 묶인 표본 기사의 정보(제목 · 요약문 · 출처 · 발행 시각 · 주제 · 지역)",
];

/** 변화 리포트를 만들 때 쓸 모델의 공급자로 가는 것 */
export const REPORT_TRANSFER_ITEMS: readonly string[] = [
  "그 기간에 추적한 흐름의 변화 · 보도량 · 관심도 통계",
  "직전 리포트의 요약",
];

/** 봇 차단을 켠 인스턴스에서 봇 차단 서비스(Cloudflare Turnstile)로 가는 것 */
export const HUMAN_CHECK_TRANSFER_ITEMS: readonly string[] = [
  "로그인 · 복구 · 가입 화면에서 브라우저가 사람 확인을 위해 Cloudflare 에 직접 보내는 정보",
  "서버가 확인을 요청할 때 보내는 확인 토큰과 IP 주소",
];

/** 고지 페이지의 주소 */
export const PRIVACY_PATH = "/privacy";
