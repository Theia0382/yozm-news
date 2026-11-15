// 남은 시간 표기 — 실패 지연의 대기와 요청 제한의 Retry-After 가 함께 쓰는 정본.

/** 남은 초를 사람이 읽는 단위로 — 1분 미만은 초, 그 이상은 분(올림). 없거나 1 미만이면 1초 */
export function remainingText(seconds: number | null): string {
  const remaining = Math.max(1, seconds ?? 1);
  return remaining < 60 ? `${remaining}초` : `${Math.ceil(remaining / 60)}분`;
}
