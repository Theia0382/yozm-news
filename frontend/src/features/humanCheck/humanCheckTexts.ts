// 봇 차단의 화면 계약 — 요청 헤더 · 위젯 동작 이름 · 안내(docs/specs/account.md account-R33).

/** 사람 확인 토큰을 싣는 요청 헤더 */
export const HUMAN_CHECK_HEADER = "X-Human-Check";

/** 위젯의 동작 이름 — 서버가 토큰의 동작과 대조한다 */
export type HumanCheckAction = "sign-in" | "account-recovery" | "sign-up";

/** 위젯 스크립트를 받지 못했다 — 제출할 수 없다 */
export const HUMAN_CHECK_UNAVAILABLE_TEXT =
  "사람 확인을 불러오지 못해 지금은 제출할 수 없습니다. 네트워크가 Cloudflare 를 막고 있지 않은지 확인하고, 잠시 뒤 페이지를 다시 열어 주세요.";

/** 사람 확인을 마치기 전 */
export const HUMAN_CHECK_PENDING_TEXT =
  "사람 확인을 마치면 제출할 수 있습니다.";
