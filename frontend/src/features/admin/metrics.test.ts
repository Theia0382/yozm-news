import { describe, expect, it } from "vitest";
import {
  formatHours,
  formatMinutes,
  formatRate,
  RATE_LEVEL_LEGEND,
  rateLevel,
} from "@/features/admin/metrics";

describe("formatRate", () => {
  it("비율을 소수 한 자리 백분율로, 비율이 없으면 없음으로 보인다", () => {
    expect(formatRate(0.756)).toBe("75.6%");
    expect(formatRate(0.3)).toBe("30.0%");
    expect(formatRate(1)).toBe("100.0%");
    expect(formatRate(0)).toBe("0.0%");
    expect(formatRate(null)).toBe("없음");
  });
});

describe("rateLevel", () => {
  it("엔트리가 없는 칸은 0% 칸과 다르다", () => {
    expect(rateLevel(null)).toBe("none");
    expect(rateLevel(0)).toBe(0);
  });

  it("구간의 위 끝은 그 구간이고 바로 위는 다음 구간이다", () => {
    expect(rateLevel(0.0001)).toBe(1);
    expect(rateLevel(0.25)).toBe(1);
    expect(rateLevel(0.2501)).toBe(2);
    expect(rateLevel(0.5)).toBe(2);
    expect(rateLevel(0.5001)).toBe(3);
    expect(rateLevel(0.75)).toBe(3);
    expect(rateLevel(0.7501)).toBe(4);
    expect(rateLevel(1)).toBe(4);
  });
});

describe("RATE_LEVEL_LEGEND", () => {
  it("단계마다 구간 경계에서 만든 문구를 순서대로 둔다", () => {
    expect(RATE_LEVEL_LEGEND).toEqual([
      { level: "none", label: "엔트리 없음" },
      { level: 0, label: "0%" },
      { level: 1, label: "25% 이하" },
      { level: 2, label: "50% 이하" },
      { level: 3, label: "75% 이하" },
      { level: 4, label: "75% 초과" },
    ]);
  });
});

describe("formatHours · formatMinutes", () => {
  it("나누어떨어지면 큰 단위로 보인다", () => {
    expect(formatHours(168)).toBe("7일");
    expect(formatHours(48)).toBe("2일");
    expect(formatHours(36)).toBe("36시간");
    expect(formatMinutes(60)).toBe("1시간");
    expect(formatMinutes(30)).toBe("30분");
    expect(formatMinutes(90)).toBe("90분");
  });
});
