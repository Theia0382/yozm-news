// 처리기 앞의 요청 방어가 내는 거부 — 요청 제한(429)과 봇 차단(403)의 문제 유형과 안내(docs/specs/user.md user-R3 · user-R40 · user-R41).

import { remainingText } from "@/shared/lib/remaining";

/** 봇 차단을 켠 인스턴스에서 사람 확인이 없거나 확인되지 않았다 — 403 */
export const HUMAN_CHECK_FAILED_TYPE =
  "urn:yozmnews:problem:human-check-failed";

/**
 * 요청 제한을 세는 축 — 그 동작을 무엇으로 세는지(docs/specs/user.md user-R38 의 정책 표). 부르는 곳이 자기 동작의 축을 적는다 — 정책
 * 표에서 동작의 축이 바뀌면 그 화면의 축도 함께 고친다.
 * - NETWORK — 클라이언트 주소로만 센다(로그인 · 복구 · 초대 · 가입)
 * - ACCOUNT — 세션 사용자로만 센다(좋아요 · 싫어요)
 * - ACCOUNT_OR_NETWORK — 둘 다로 센다(키 확인). 어느 쪽에 걸렸는지 응답이 알리지 않는다
 */
export type RateLimitAxis = "NETWORK" | "ACCOUNT" | "ACCOUNT_OR_NETWORK";

/**
 * 요청 제한 안내의 앞부분 — 클라이언트 주소 축은 로그인의 실패 지연(이메일마다)과 다르게 이 네트워크의 요청이 많다는 뜻이고, 세션 사용자
 * 축은 이 계정의 요청이 많다는 뜻이다. 두 축이면 축을 말하지 않는다
 */
const rateLimitedLeads = {
  NETWORK: "이 네트워크에서 요청이 너무 많아",
  ACCOUNT: "이 계정의 요청이 너무 많아",
  ACCOUNT_OR_NETWORK: "요청이 너무 많아",
} as const satisfies Record<RateLimitAxis, string>;

/** 요청 제한 안내 — 그 동작을 세는 축의 문장과 남은 시간 */
export function rateLimitedText(
  seconds: number | null,
  axis: RateLimitAxis,
): string {
  return `${rateLimitedLeads[axis]} 잠시 막혔습니다. ${remainingText(seconds)} 뒤에 다시 시도해 주세요.`;
}

/** 사람 확인 실패 안내 */
export const HUMAN_CHECK_FAILED_TEXT =
  "사람 확인에 실패했습니다. 확인을 다시 마친 뒤 제출해 주세요.";

/**
 * 요청 방어의 거부면 그 안내, 아니면 null — 화면은 먼저 이것을 보고 그 밖의 실패 문구로 넘어간다.
 * @param problem 응답 본문(문제 응답) — openapi-fetch 의 error
 * @param axis 그 처리기의 요청 제한을 세는 축 — 429 안내의 문장을 가른다
 */
export function guardRejectionText(
  response: Response,
  problem: unknown,
  axis: RateLimitAxis,
): string | null {
  if (response.status === 429) {
    return rateLimitedText(retryAfterSeconds(response), axis);
  }
  if (
    response.status === 403 &&
    problemType(problem) === HUMAN_CHECK_FAILED_TYPE
  ) {
    return HUMAN_CHECK_FAILED_TEXT;
  }
  return null;
}

/** Retry-After 의 초 — 정수가 아니면 null */
function retryAfterSeconds(response: Response): number | null {
  const header = response.headers.get("Retry-After");
  return header !== null && /^\d+$/.test(header) ? Number(header) : null;
}

function problemType(problem: unknown): string | null {
  return typeof problem === "object" &&
    problem !== null &&
    "type" in problem &&
    typeof problem.type === "string"
    ? problem.type
    : null;
}
