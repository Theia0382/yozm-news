// 오늘 다이제스트 안내의 시각 판단 — 관심 설정의 생성 시각 · 시간대로 지금이 그 시각 전인지 본다(ADR-0237). 따라잡기 창은 서버가 정하므로 여기서 셈하지 않는다.

/** 그 시간대의 지금 시각(HH:MM, 24시간) — 시간대를 읽지 못하면 null */
function clockIn(now: Date, zone: string): string | null {
  try {
    const parts = new Intl.DateTimeFormat("en-GB", {
      timeZone: zone,
      hour: "2-digit",
      minute: "2-digit",
      hourCycle: "h23",
    }).formatToParts(now);
    const hour = parts.find((p) => p.type === "hour")?.value;
    const minute = parts.find((p) => p.type === "minute")?.value;
    return hour !== undefined && minute !== undefined
      ? `${hour}:${minute}`
      : null;
  } catch {
    return null;
  }
}

/**
 * 지금이 그 시간대의 오늘 생성 시각 전인가.
 * @param time 생성 시각(HH:MM)
 * @return 시간대를 읽지 못하면 null — 화면은 시각 없는 문구로 낮춘다
 */
export function beforeGenerationTime(
  now: Date,
  time: string,
  zone: string,
): boolean | null {
  const clock = clockIn(now, zone);
  return clock === null ? null : clock < time.slice(0, 5);
}

/** 생성 시각에 붙일 시간대 이름 — 브라우저 시간대와 같으면 붙이지 않는다 */
export function zoneLabel(zone: string): string | null {
  return Intl.DateTimeFormat().resolvedOptions().timeZone === zone
    ? null
    : zone;
}
