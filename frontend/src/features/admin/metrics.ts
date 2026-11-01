// 수집 현황의 표시 규칙 — 서버가 낸 비율 · 기간을 글자와 칸 색으로 옮기기만 한다. 비율은 계산하지 않는다(정본은 서버 — article-R14).

/** 비율 표시 — 소수 한 자리 백분율. 엔트리가 없어 비율이 없으면(null) "없음" 이다(0% 가 아니다) */
export function formatRate(rate: number | null): string {
  return rate === null ? "없음" : `${(rate * 100).toFixed(1)}%`;
}

/** 칸 색의 단계 — "none" 은 엔트리가 없는 칸, 0 은 0%, 1 ~ 4 는 25% 씩 올라가는 구간(위 끝 포함) */
export type RateLevel = "none" | 0 | 1 | 2 | 3 | 4;

/** 비율의 칸 색 단계 — 0% · ~25% · ~50% · ~75% · 그 위. 좋고 나쁨이 아니라 크기만 나타낸다 */
export function rateLevel(rate: number | null): RateLevel {
  if (rate === null) {
    return "none";
  }
  if (rate <= 0) {
    return 0;
  }
  if (rate <= 0.25) {
    return 1;
  }
  if (rate <= 0.5) {
    return 2;
  }
  if (rate <= 0.75) {
    return 3;
  }
  return 4;
}

/** 단계마다 한 가지 색의 진하기 — 엔트리가 없는 칸은 빗금 */
export const RATE_LEVEL_CLASSES = {
  none: "border-dashed border-border bg-[repeating-linear-gradient(45deg,var(--color-border)_0_2px,transparent_2px_6px)]",
  0: "border-border bg-background",
  1: "border-primary/20 bg-primary/20",
  2: "border-primary/40 bg-primary/40",
  3: "border-primary/65 bg-primary/65",
  4: "border-primary bg-primary",
} as const satisfies Record<RateLevel, string>;

/** 시간 수 표시 — 24 의 배수면 일로 */
export function formatHours(hours: number): string {
  return hours % 24 === 0 ? `${hours / 24}일` : `${hours}시간`;
}

/** 분 수 표시 — 60 의 배수면 시간으로 */
export function formatMinutes(minutes: number): string {
  return minutes % 60 === 0 ? `${minutes / 60}시간` : `${minutes}분`;
}
