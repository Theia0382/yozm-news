/**
 * 화면의 시각 표기 — 정본. 시각을 그리는 곳은 모두 이 함수를 부른다.
 *
 * 읽는 사람의 시간대(브라우저 시간대, 또는 준 시간대)로 보이고, 같은 시각이 시간대마다 다르게 보이므로 시간대 이름을 붙인다.
 * 초 이하는 보이지 않는다. 읽지 못하는 값은 받은 문자열을 그대로 돌려준다 — 서버 값이 형식을 벗어나도 화면이 깨지지 않게.
 *
 * @param value ISO 8601 시각 문자열(서버 응답의 `date-time`)
 * @param timeZone IANA 시간대 — 없으면 브라우저 시간대
 */
export function formatDateTime(value: string, timeZone?: string): string {
  const instant = new Date(value);
  if (Number.isNaN(instant.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "numeric",
    minute: "2-digit",
    timeZone,
    timeZoneName: "short",
  }).format(instant);
}
