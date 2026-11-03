// 운영자 화면(수집 현황 · 평가 현황)의 표시 규칙 — 서버가 낸 비율 · 기간을 글자와 칸 색으로 옮기기만 한다. 비율은 계산하지 않는다(정본은 서버 — article-R14 · evaluation-R29).

/** 비율 표시 — 소수 한 자리 백분율. 분모가 없어(엔트리 · 평가가 0) 비율이 없으면(null) "없음" 이다(0% 가 아니다) */
export function formatRate(rate: number | null): string {
  return rate === null ? "없음" : `${(rate * 100).toFixed(1)}%`;
}

/** 칸 색의 단계 — "none" 은 엔트리가 없는 칸, 0 은 0%, 1 ~ 4 는 구간 경계로 나눈 단계 */
export type RateLevel = "none" | 0 | 1 | 2 | 3 | 4;

/** 구간 경계의 정본 — 1 ~ 3 단계의 위 끝(포함). 그 위는 4 단계. 칸 색과 범례가 함께 쓴다 */
export const RATE_LEVEL_BOUNDS = [0.25, 0.5, 0.75] as const;

/** 비율의 칸 색 단계 — 좋고 나쁨이 아니라 크기만 나타낸다 */
export function rateLevel(rate: number | null): RateLevel {
  if (rate === null) {
    return "none";
  }
  if (rate <= 0) {
    return 0;
  }
  const [first, second, third] = RATE_LEVEL_BOUNDS;
  if (rate <= first) {
    return 1;
  }
  if (rate <= second) {
    return 2;
  }
  if (rate <= third) {
    return 3;
  }
  return 4;
}

function percent(bound: number): string {
  return `${Math.round(bound * 100)}%`;
}

/** 범례 — 단계마다 문구. 문구는 구간 경계에서 만든다 */
export const RATE_LEVEL_LEGEND: readonly { level: RateLevel; label: string }[] =
  [
    { level: "none", label: "엔트리 없음" },
    { level: 0, label: "0%" },
    { level: 1, label: `${percent(RATE_LEVEL_BOUNDS[0])} 이하` },
    { level: 2, label: `${percent(RATE_LEVEL_BOUNDS[1])} 이하` },
    { level: 3, label: `${percent(RATE_LEVEL_BOUNDS[2])} 이하` },
    { level: 4, label: `${percent(RATE_LEVEL_BOUNDS[2])} 초과` },
  ];

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
