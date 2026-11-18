// 오늘 다이제스트 안내의 시각 적기 — 서버가 정한 오늘 일정 생성 시각(digest-R40)을 사용자 시간대로 적는다. 판단은 서버가 한다(ADR-0237).

/**
 * 그 시간대에서 그 시각(HH:MM, 24시간).
 * @param instant ISO-8601 시각
 * @return 시간대를 읽지 못하면 null — 화면은 시각 없는 문구로 낮춘다
 */
export function clockIn(instant: string, zone: string): string | null {
  try {
    const parts = new Intl.DateTimeFormat("en-GB", {
      timeZone: zone,
      hour: "2-digit",
      minute: "2-digit",
      hourCycle: "h23",
    }).formatToParts(new Date(instant));
    const hour = parts.find((p) => p.type === "hour")?.value;
    const minute = parts.find((p) => p.type === "minute")?.value;
    return hour !== undefined && minute !== undefined
      ? `${hour}:${minute}`
      : null;
  } catch {
    return null;
  }
}

/** 생성 시각에 붙일 시간대 이름 — 브라우저 시간대와 같으면 붙이지 않는다 */
export function zoneLabel(zone: string): string | null {
  return Intl.DateTimeFormat().resolvedOptions().timeZone === zone
    ? null
    : zone;
}
