import { describe, expect, it } from "vitest";
import { formatDateTime } from "@/shared/lib/dateTime";

describe("formatDateTime", () => {
  it("준 시간대의 지역 표기와 시간대 이름으로 보인다", () => {
    expect(formatDateTime("2026-10-28T00:00:00Z", "Asia/Seoul")).toBe(
      "2026. 10. 28. 오전 9:00 GMT+9",
    );
    expect(formatDateTime("2026-10-28T00:00:00Z", "America/New_York")).toBe(
      "2026. 10. 27. 오후 8:00 GMT-4",
    );
  });

  it("초 이하는 보이지 않는다 — 9자리 소수 초도 읽는다", () => {
    expect(formatDateTime("2026-10-31T11:45:38.554487584Z", "Asia/Seoul")).toBe(
      "2026. 10. 31. 오후 8:45 GMT+9",
    );
  });

  it("시간대를 주지 않으면 브라우저(실행 환경) 시간대로 보인다", () => {
    // 시험 실행 환경의 시간대는 vite.config.ts 의 test.env 가 Asia/Seoul 로 고정한다
    expect(formatDateTime("2026-10-28T06:30:00Z")).toBe(
      "2026. 10. 28. 오후 3:30 GMT+9",
    );
  });

  it("읽지 못하는 값은 받은 문자열 그대로 돌려준다", () => {
    expect(formatDateTime("시각 아님", "Asia/Seoul")).toBe("시각 아님");
  });
});
