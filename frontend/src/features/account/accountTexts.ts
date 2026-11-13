import type { components } from "@/shared/api/schema";

// 계정 화면의 안내 문구 — 결과 판별자 · 이유마다 문구 하나(망라 switch).

export type PasswordProblem = NonNullable<
  components["schemas"]["SignUpResponse"]["passwordProblem"]
>;

export type InvitationCheckState =
  components["schemas"]["InvitationCheckResponse"]["state"];

/** 비밀번호 규칙을 어긴 까닭 — 길이의 정본은 서버의 비밀번호 규칙이라 수를 적지 않는다 */
export function passwordProblemText(problem: PasswordProblem | null): string {
  if (problem === null) {
    return "비밀번호 규칙에 맞지 않습니다.";
  }
  switch (problem) {
    case "TOO_SHORT":
      return "비밀번호가 너무 짧습니다. 더 길게 정해 주세요.";
    case "TOO_LONG":
      return "비밀번호가 너무 깁니다. 더 짧게 정해 주세요.";
  }
  const unknown: never = problem;
  return `비밀번호 규칙에 맞지 않습니다: ${String(unknown)}`;
}

/** 쓸 수 없는 초대 링크의 안내 */
export function invitationStateText(state: InvitationCheckState): string {
  switch (state) {
    case "VALID":
      return "쓸 수 있는 초대 링크입니다.";
    case "USED":
      return "이미 쓴 초대 링크입니다.";
    case "EXPIRED":
      return "기한이 지난 초대 링크입니다. 운영자에게 새 링크를 받으세요.";
    case "REVOKED":
      return "취소된 초대 링크입니다. 운영자에게 새 링크를 받으세요.";
    case "UNKNOWN":
      return "없는 초대 링크입니다. 링크를 빠짐없이 열었는지 확인해 주세요.";
  }
  const unknown: never = state;
  return `알 수 없는 초대 상태입니다: ${String(unknown)}`;
}

/** 서버가 판정 결과 대신 오류로 답했을 때 */
export function requestFailedText(status: number): string {
  return `요청이 실패했습니다 (HTTP ${status}). 다시 시도해 주세요.`;
}

/** 지금 비밀번호를 확인하는 요청(비밀번호 변경 · 복구 코드 새로 받기)에서 지금 비밀번호가 틀렸을 때 */
export const CURRENT_PASSWORD_REJECTED_TEXT = "지금 비밀번호가 맞지 않습니다.";

/** 지금 비밀번호 확인이 막혔을 때 대기 안내의 시도 이름 — 로그인과 같은 실패 기록을 쓴다 */
export const CURRENT_PASSWORD_ATTEMPT = "비밀번호 확인";

/** 비밀번호와 확인이 다를 때 — 서버에 보내지 않는다 */
export const PASSWORD_MISMATCH_TEXT = "비밀번호와 확인이 서로 다릅니다.";

/** 서버에 닿지 못했을 때 */
export const UNREACHABLE_TEXT = "서버에 닿지 못했습니다.";
