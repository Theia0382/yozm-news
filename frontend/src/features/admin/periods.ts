// 운영자 화면의 기간(시작일 · 종료일 · 시간대) — 평가 현황 · 평가 결과 · 표본 검수 · 태그 기준이 함께 쓴다. 기간 상한의 정본은 서버의 400 이다.

/** 기본 기간 — 오늘을 포함한 이 날수 */
export const DEFAULT_DAYS = 7;

/** 날짜 하나(yyyy-MM-dd)의 하루 길이 — 날짜 더하기는 그 날짜의 UTC 자정에서 한다(시간대 전환과 무관) */
const DAY_MS = 24 * 60 * 60 * 1000;

export type Period = { from: string; to: string; zone: string };

/** 이 브라우저의 시간대(IANA ID) */
export function browserZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

/** 그 시간대의 오늘(yyyy-MM-dd) */
export function today(zone: string): string {
  // en-CA 형식이 yyyy-MM-dd 다
  return new Intl.DateTimeFormat("en-CA", { timeZone: zone }).format(
    new Date(),
  );
}

/** 날짜에 날수를 더한다 */
export function addDays(date: string, days: number): string {
  return new Date(Date.parse(`${date}T00:00:00Z`) + days * DAY_MS)
    .toISOString()
    .slice(0, 10);
}

/** 오늘을 포함한 기본 기간의 시작일 */
export function defaultFirst(zone: string): string {
  return addDays(today(zone), 1 - DEFAULT_DAYS);
}

/** 시작일 ~ 종료일의 날수 — 끝 포함 */
export function dayCount(first: string, last: string): number {
  return (
    Math.round(
      (Date.parse(`${last}T00:00:00Z`) - Date.parse(`${first}T00:00:00Z`)) /
        DAY_MS,
    ) + 1
  );
}

/** 양식 안내 — 서버의 400 이 정본이고, 이것은 보내기 전에 알리는 것뿐이다 */
export function periodHint(
  first: string,
  last: string,
  maxDays: number | null,
): string | null {
  if (first === "" || last === "") {
    return "시작일과 종료일을 고른다";
  }
  const days = dayCount(first, last);
  if (days < 1) {
    return "종료일이 시작일보다 앞선다";
  }
  if (maxDays !== null && days > maxDays) {
    return `기간은 ${maxDays}일까지다 — 지금 ${days}일`;
  }
  return null;
}
