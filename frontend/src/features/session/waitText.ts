// 시도 대기 안내 — 남은 초를 사람이 읽는 단위로 바꾼다(docs/specs/user.md user-R17).

/** 기다리라는 안내 — 1분 미만은 초, 그 이상은 분(올림). attempt 는 막힌 시도의 이름 */
export function waitText(seconds: number | null, attempt = "로그인"): string {
  const remaining = Math.max(1, seconds ?? 1);
  const when =
    remaining < 60 ? `${remaining}초` : `${Math.ceil(remaining / 60)}분`;
  return `${attempt} 시도가 많아 잠시 막혔습니다. ${when} 뒤에 다시 시도해 주세요.`;
}
