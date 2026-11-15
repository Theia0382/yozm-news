// 처리기 앞의 요청 방어가 내는 거부 — 요청 제한(429)과 봇 차단(403)의 문제 유형과 안내(docs/specs/user.md user-R3 · user-R40 · user-R41).

import { remainingText } from "@/shared/lib/remaining";

/** 봇 차단을 켠 인스턴스에서 사람 확인이 없거나 확인되지 않았다 — 403 */
export const HUMAN_CHECK_FAILED_TYPE =
  "urn:yozmnews:problem:human-check-failed";

/** 요청 제한 안내 — 로그인의 실패 지연(이메일마다)과 다르게, 이 네트워크의 요청이 많다는 뜻이다 */
export function rateLimitedText(seconds: number | null): string {
  return `이 네트워크에서 요청이 너무 많아 잠시 막혔습니다. ${remainingText(seconds)} 뒤에 다시 시도해 주세요.`;
}

/** 사람 확인 실패 안내 */
export const HUMAN_CHECK_FAILED_TEXT =
  "사람 확인에 실패했습니다. 확인을 다시 마친 뒤 제출해 주세요.";

/**
 * 요청 방어의 거부면 그 안내, 아니면 null — 화면은 먼저 이것을 보고 그 밖의 실패 문구로 넘어간다.
 * @param problem 응답 본문(문제 응답) — openapi-fetch 의 error
 */
export function guardRejectionText(
  response: Response,
  problem: unknown,
): string | null {
  if (response.status === 429) {
    return rateLimitedText(retryAfterSeconds(response));
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
