import { describe, expect, it } from "vitest";
import { waitText } from "@/features/session/waitText";

describe("waitText", () => {
  it("1분 미만은 초, 그 이상은 분으로 올림한다", () => {
    expect(waitText(1)).toBe(
      "로그인 시도가 많아 잠시 막혔습니다. 1초 뒤에 다시 시도해 주세요.",
    );
    expect(waitText(59)).toContain("59초 뒤");
    expect(waitText(60)).toContain("1분 뒤");
    expect(waitText(900)).toContain("15분 뒤");
    expect(waitText(null)).toContain("1초 뒤");
  });
});
